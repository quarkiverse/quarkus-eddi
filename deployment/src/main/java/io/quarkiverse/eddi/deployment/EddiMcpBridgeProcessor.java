package io.quarkiverse.eddi.deployment;

import java.lang.reflect.Modifier;
import java.util.*;

import org.jboss.jandex.*;
import org.jboss.logging.Logger;

import io.quarkiverse.eddi.annotations.EddiTool;
import io.quarkiverse.eddi.annotations.ToolArg;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.GeneratedBeanBuildItem;
import io.quarkus.arc.deployment.GeneratedBeanGizmoAdaptor;
import io.quarkus.bootstrap.classloading.QuarkusClassLoader;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.gizmo.*;

/**
 * Build step processor that exposes {@link EddiTool} methods as Quarkus MCP Server
 * tools, so EDDI agents can call back into the application.
 * <p>
 * For every class declaring {@code @EddiTool} methods a delegate bean
 * {@code <Class>_EddiMcpTools} is generated. Each of its methods carries real
 * {@code @io.quarkiverse.mcp.server.Tool} / {@code @ToolArg} annotations and
 * forwards to the user's bean. (Annotation transformers are not enough: the MCP
 * server extension reads the declared annotations of bean classes.)
 * <p>
 * The MCP annotations are referenced by name, so the extension has no compile
 * dependency on {@code quarkus-mcp-server-http}; nothing is generated unless the
 * application has it on the classpath.
 */
public class EddiMcpBridgeProcessor {

    private static final Logger LOG = Logger.getLogger(EddiMcpBridgeProcessor.class);
    private static final DotName EDDI_TOOL = DotName.createSimple(EddiTool.class.getName());
    private static final DotName EDDI_TOOL_ARG = DotName.createSimple(ToolArg.class.getName());

    static final String MCP_TOOL = "io.quarkiverse.mcp.server.Tool";
    static final String MCP_TOOL_ARG = "io.quarkiverse.mcp.server.ToolArg";

    @BuildStep
    void processEddiToolAnnotations(CombinedIndexBuildItem combinedIndex,
            BuildProducer<AdditionalBeanBuildItem> additionalBeans,
            BuildProducer<GeneratedBeanBuildItem> generatedBeans) {

        var annotations = combinedIndex.getIndex().getAnnotations(EDDI_TOOL);
        if (annotations.isEmpty()) {
            return;
        }

        if (!QuarkusClassLoader.isClassPresentAtRuntime(MCP_TOOL)) {
            LOG.warnf("Found %d @EddiTool methods but quarkus-mcp-server-http is not on the classpath. "
                    + "Add the dependency to expose them as MCP tools.", annotations.size());
            return;
        }

        // Group tool methods by declaring class (sorted for reproducible builds)
        Map<ClassInfo, List<MethodInfo>> toolsByClass = new TreeMap<>(Comparator.comparing(c -> c.name().toString()));
        for (AnnotationInstance annotation : annotations) {
            MethodInfo method = annotation.target().asMethod();
            validate(method);
            toolsByClass.computeIfAbsent(method.declaringClass(), c -> new ArrayList<>()).add(method);
        }

        var adaptor = new GeneratedBeanGizmoAdaptor(generatedBeans);
        for (var entry : toolsByClass.entrySet()) {
            String className = entry.getKey().name().toString();

            // The user's class must be a bean so the delegate can inject it
            additionalBeans.produce(AdditionalBeanBuildItem.builder()
                    .addBeanClass(className)
                    .setDefaultScope(DotName.createSimple("jakarta.enterprise.context.ApplicationScoped"))
                    .setUnremovable()
                    .build());

            generateDelegate(adaptor, className, entry.getValue());
        }

        LOG.infof("Exposed %d @EddiTool methods from %d classes as MCP tools",
                annotations.size(), toolsByClass.size());
    }

    private static void validate(MethodInfo method) {
        if (Modifier.isStatic(method.flags()) || Modifier.isPrivate(method.flags())) {
            throw new IllegalStateException(String.format(
                    "@EddiTool method %s.%s must be a non-private instance method",
                    method.declaringClass().name(), method.name()));
        }
        for (int i = 0; i < method.parametersCount(); i++) {
            if (method.parameterName(i) == null) {
                throw new IllegalStateException(String.format(
                        "@EddiTool method %s.%s: parameter names are not available; compile with -parameters",
                        method.declaringClass().name(), method.name()));
            }
        }
    }

    private static void generateDelegate(ClassOutput output, String className, List<MethodInfo> tools) {
        String delegateName = className + "_EddiMcpTools";

        try (ClassCreator creator = ClassCreator.builder()
                .classOutput(output)
                .className(delegateName)
                .build()) {

            creator.addAnnotation("jakarta.inject.Singleton");

            // Package-private so ArC can inject it directly
            FieldCreator delegateField = creator.getFieldCreator("delegate", className);
            delegateField.setModifiers(0);
            delegateField.addAnnotation("jakarta.inject.Inject");
            FieldDescriptor delegate = delegateField.getFieldDescriptor();

            for (MethodInfo tool : tools) {
                generateToolMethod(creator, delegate, className, tool);
            }
        }
    }

    private static void generateToolMethod(ClassCreator creator, FieldDescriptor delegate, String className,
            MethodInfo tool) {
        AnnotationInstance eddiTool = tool.annotation(EDDI_TOOL);
        String description = eddiTool.value("description").asString();
        AnnotationValue nameValue = eddiTool.value("name");
        String toolName = nameValue != null && !nameValue.asString().isEmpty() ? nameValue.asString() : tool.name();

        MethodDescriptor target = MethodDescriptor.of(tool);
        try (MethodCreator method = creator.getMethodCreator(
                MethodDescriptor.ofMethod(creator.getClassName(), tool.name(),
                        target.getReturnType(), target.getParameterTypes()))) {

            method.setModifiers(Modifier.PUBLIC);
            if (tool.requiresGenericSignature()) {
                // Keep generics so MCP derives the right JSON schema (e.g. List<String>)
                method.setSignature(tool.genericSignature(typeVariable -> null));
            }
            String[] parameterNames = new String[tool.parametersCount()];
            for (int i = 0; i < tool.parametersCount(); i++) {
                parameterNames[i] = tool.parameterName(i);
            }
            method.setParameterNames(parameterNames);

            AnnotationCreator mcpTool = method.addAnnotation(MCP_TOOL);
            mcpTool.addValue("name", toolName);
            mcpTool.addValue("description", description);

            for (int i = 0; i < tool.parametersCount(); i++) {
                AnnotationInstance eddiArg = tool.parameters().get(i).declaredAnnotation(EDDI_TOOL_ARG);
                AnnotationCreator mcpArg = method.getParameterAnnotations(i).addAnnotation(MCP_TOOL_ARG);
                mcpArg.addValue("name", parameterNames[i]);
                if (eddiArg != null) {
                    AnnotationValue argDescription = eddiArg.value("description");
                    if (argDescription != null) {
                        mcpArg.addValue("description", argDescription.asString());
                    }
                    AnnotationValue required = eddiArg.value("required");
                    if (required != null) {
                        mcpArg.addValue("required", required.asBoolean());
                    }
                }
            }

            ResultHandle bean = method.readInstanceField(delegate, method.getThis());
            ResultHandle[] args = new ResultHandle[tool.parametersCount()];
            for (int i = 0; i < args.length; i++) {
                args[i] = method.getMethodParam(i);
            }
            ResultHandle result = method.invokeVirtualMethod(target, bean, args);
            method.returnValue(target.getReturnType().equals("V") ? null : result);

            LOG.debugf("Bridged @EddiTool %s#%s → MCP tool '%s'", className, tool.name(), toolName);
        }
    }
}
