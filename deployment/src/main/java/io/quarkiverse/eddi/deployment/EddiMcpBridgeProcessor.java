package io.quarkiverse.eddi.deployment;

import java.util.*;

import org.jboss.jandex.*;
import org.jboss.logging.Logger;

import io.quarkiverse.eddi.annotations.EddiTool;
import io.quarkiverse.eddi.annotations.ToolArg;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.AnnotationsTransformerBuildItem;
import io.quarkus.arc.processor.AnnotationsTransformer;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;

/**
 * Build step processor that scans for {@link EddiTool} annotations and bridges
 * them to Quarkus MCP Server's {@code @Tool} / {@code @ToolArg} annotations.
 * <p>
 * EDDI v6 includes a native MCP server with {@code McpAdminTools},
 * {@code McpConversationTools}, etc. This bridge lets SDK users expose
 * <em>their own</em> CDI methods as additional MCP tools that EDDI discovers.
 * <p>
 * The implementation uses Quarkus's {@link AnnotationsTransformerBuildItem} to
 * transparently add {@code @io.quarkiverse.mcp.server.Tool} and
 * {@code @io.quarkiverse.mcp.server.ToolArg} annotations at build time.
 * This means the Quarkus MCP Server extension handles all registration,
 * schema generation, and runtime invocation automatically.
 * <p>
 * The flow:
 * <ol>
 * <li>At build time: transform {@code @EddiTool} → {@code @Tool},
 * {@code @ToolArg} → MCP {@code @ToolArg}</li>
 * <li>The MCP server extension discovers and registers the tools</li>
 * <li>At runtime: EDDI calls the MCP endpoint to invoke tools during
 * conversation processing</li>
 * </ol>
 */
public class EddiMcpBridgeProcessor {

    private static final Logger LOG = Logger.getLogger(EddiMcpBridgeProcessor.class);
    private static final DotName EDDI_TOOL = DotName.createSimple(EddiTool.class.getName());
    private static final DotName EDDI_TOOL_ARG = DotName.createSimple(ToolArg.class.getName());

    // MCP Server annotations — referenced by name to avoid hard compile dependency
    private static final DotName MCP_TOOL = DotName.createSimple("io.quarkiverse.mcp.server.Tool");
    private static final DotName MCP_TOOL_ARG = DotName.createSimple("io.quarkiverse.mcp.server.ToolArg");

    @BuildStep
    void processEddiToolAnnotations(CombinedIndexBuildItem combinedIndex,
            BuildProducer<AdditionalBeanBuildItem> additionalBeans,
            BuildProducer<AnnotationsTransformerBuildItem> annotationsTransformers) {

        var index = combinedIndex.getIndex();
        var annotations = index.getAnnotations(EDDI_TOOL);

        if (annotations.isEmpty()) {
            return;
        }

        // Check if MCP server is on the classpath
        boolean mcpAvailable = isMcpServerAvailable();
        if (!mcpAvailable) {
            LOG.warnf("Found %d @EddiTool methods but quarkus-mcp-server-http is not on the classpath. "
                    + "Add the dependency to enable MCP tool bridge.", annotations.size());
            return;
        }

        // Track declaring classes to register them as CDI beans
        Set<String> declaringClasses = new HashSet<>();
        Set<String> toolMethodIds = new HashSet<>();

        for (AnnotationInstance annotation : annotations) {
            MethodInfo method = annotation.target().asMethod();
            ClassInfo declaringClass = method.declaringClass();
            String className = declaringClass.name().toString();
            declaringClasses.add(className);

            String description = annotation.value("description").asString();
            var nameValue = annotation.value("name");
            String toolName = (nameValue != null && !nameValue.asString().isEmpty())
                    ? nameValue.asString()
                    : method.name();

            // Build a unique key for this method to match in the transformer
            String methodId = className + "#" + method.name();
            toolMethodIds.add(methodId);

            LOG.infof("Bridging @EddiTool → @Tool: %s#%s → name=%s, description=%s",
                    className, method.name(), toolName, description);
        }

        // Register declaring classes as CDI beans
        for (String className : declaringClasses) {
            additionalBeans.produce(AdditionalBeanBuildItem.builder()
                    .addBeanClass(className)
                    .setDefaultScope(DotName.createSimple("jakarta.enterprise.context.ApplicationScoped"))
                    .setUnremovable()
                    .build());
        }

        // Add annotation transformer that bridges @EddiTool → @Tool and @ToolArg → MCP @ToolArg
        annotationsTransformers.produce(new AnnotationsTransformerBuildItem(
                new EddiToolAnnotationsTransformer()));

        LOG.infof("Registered %d @EddiTool methods from %d classes for MCP bridge",
                annotations.size(), declaringClasses.size());
    }

    /**
     * Check if the MCP server classes are available on the classpath.
     */
    private boolean isMcpServerAvailable() {
        try {
            Class.forName("io.quarkiverse.mcp.server.Tool", false,
                    Thread.currentThread().getContextClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * Annotations transformer that bridges EDDI SDK annotations to MCP Server annotations.
     */
    private static class EddiToolAnnotationsTransformer implements AnnotationsTransformer {

        @Override
        public boolean appliesTo(AnnotationTarget.Kind kind) {
            return kind == AnnotationTarget.Kind.METHOD || kind == AnnotationTarget.Kind.METHOD_PARAMETER;
        }

        @Override
        public void transform(TransformationContext context) {
            AnnotationTarget target = context.getTarget();

            if (target.kind() == AnnotationTarget.Kind.METHOD) {
                transformMethod(context);
            } else if (target.kind() == AnnotationTarget.Kind.METHOD_PARAMETER) {
                transformParameter(context);
            }
        }

        private void transformMethod(TransformationContext context) {
            MethodInfo method = context.getTarget().asMethod();
            AnnotationInstance eddiTool = method.annotation(EDDI_TOOL);
            if (eddiTool == null) {
                return;
            }

            // Extract the description
            String description = eddiTool.value("description").asString();
            var nameValue = eddiTool.value("name");
            String toolName = (nameValue != null && !nameValue.asString().isEmpty())
                    ? nameValue.asString()
                    : method.name();

            // Add @io.quarkiverse.mcp.server.Tool(description = "...")
            // The MCP server extension uses "name" for the tool name (sourced from method name by default)
            context.transform()
                    .add(MCP_TOOL, AnnotationValue.createStringValue("description", description))
                    .done();
        }

        private void transformParameter(TransformationContext context) {
            MethodParameterInfo paramInfo = context.getTarget().asMethodParameter();
            MethodInfo method = paramInfo.method();

            // Only transform parameters on @EddiTool-annotated methods
            if (method.annotation(EDDI_TOOL) == null) {
                return;
            }

            // Check if this parameter has @ToolArg
            AnnotationInstance eddiToolArg = null;
            for (AnnotationInstance ann : method.annotations()) {
                if (ann.name().equals(EDDI_TOOL_ARG)
                        && ann.target().kind() == AnnotationTarget.Kind.METHOD_PARAMETER
                        && ann.target().asMethodParameter().position() == paramInfo.position()) {
                    eddiToolArg = ann;
                    break;
                }
            }

            if (eddiToolArg != null) {
                // Transform @ToolArg → @io.quarkiverse.mcp.server.ToolArg
                var descValue = eddiToolArg.value("description");
                String description = descValue != null ? descValue.asString() : "";

                context.transform()
                        .add(MCP_TOOL_ARG, AnnotationValue.createStringValue("description", description))
                        .done();
            }
        }
    }
}
