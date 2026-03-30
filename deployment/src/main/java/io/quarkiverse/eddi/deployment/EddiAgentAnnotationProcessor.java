package io.quarkiverse.eddi.deployment;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.jboss.jandex.*;
import org.jboss.logging.Logger;

import io.quarkiverse.eddi.Conversation;
import io.quarkiverse.eddi.EddiClient;
import io.quarkiverse.eddi.annotations.EddiAgent;
import io.quarkiverse.eddi.annotations.OnMessage;
import io.quarkiverse.eddi.annotations.OnResponse;
import io.quarkiverse.eddi.model.ConversationResult;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.GeneratedBeanBuildItem;
import io.quarkus.arc.deployment.GeneratedBeanGizmoAdaptor;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.gizmo.*;

/**
 * Build step processor that scans for {@link EddiAgent} annotations and generates
 * JAX-RS endpoint resources at build time using Gizmo bytecode generation.
 * <p>
 * For each class annotated with {@code @EddiAgent}, this processor generates
 * a JAX-RS resource that:
 * <ol>
 * <li>Manages a per-user conversation map (ConcurrentHashMap) to reuse conversations</li>
 * <li>Invokes {@code @OnMessage} hooks before sending to EDDI</li>
 * <li>Invokes {@code @OnResponse} hooks after receiving the response</li>
 * <li>Optionally exposes an SSE streaming endpoint when {@code streaming = true}</li>
 * </ol>
 */
public class EddiAgentAnnotationProcessor {

    private static final Logger LOG = Logger.getLogger(EddiAgentAnnotationProcessor.class);
    private static final DotName EDDI_AGENT = DotName.createSimple(EddiAgent.class.getName());
    private static final DotName ON_MESSAGE = DotName.createSimple(OnMessage.class.getName());
    private static final DotName ON_RESPONSE = DotName.createSimple(OnResponse.class.getName());

    @BuildStep
    void processEddiAgentAnnotations(CombinedIndexBuildItem combinedIndex,
            BuildProducer<GeneratedBeanBuildItem> generatedBeanProducer,
            BuildProducer<AdditionalBeanBuildItem> additionalBeans) {

        var index = combinedIndex.getIndex();
        var annotations = index.getAnnotations(EDDI_AGENT);

        for (AnnotationInstance annotation : annotations) {
            ClassInfo classInfo = annotation.target().asClass();
            String agentId = annotation.value("id").asString();
            String pathValue = annotation.value("path").asString();
            boolean streaming = annotation.value("streaming") != null
                    && annotation.value("streaming").asBoolean();
            String environment = annotation.value("environment") != null
                    ? annotation.value("environment").asString()
                    : "";

            String className = classInfo.name().toString();
            String generatedName = className + "_EddiResource";

            LOG.infof("Generating @EddiAgent resource: %s → agent=%s, path=%s, streaming=%s",
                    generatedName, agentId, pathValue, streaming);

            // Discover hooks
            List<MethodInfo> onMessageMethods = classInfo.methods().stream()
                    .filter(m -> m.hasAnnotation(ON_MESSAGE))
                    .toList();
            List<MethodInfo> onResponseMethods = classInfo.methods().stream()
                    .filter(m -> m.hasAnnotation(ON_RESPONSE))
                    .toList();

            generateResourceClass(generatedBeanProducer, generatedName, classInfo,
                    agentId, pathValue, streaming, environment,
                    onMessageMethods, onResponseMethods);

            // Register the annotated class as a CDI bean so we can invoke its hooks
            additionalBeans.produce(AdditionalBeanBuildItem.builder()
                    .addBeanClass(className)
                    .setDefaultScope(DotName.createSimple("jakarta.enterprise.context.ApplicationScoped"))
                    .setUnremovable()
                    .build());
        }
    }

    private void generateResourceClass(BuildProducer<GeneratedBeanBuildItem> producer,
            String generatedName, ClassInfo annotatedClass,
            String agentId, String pathValue, boolean streaming, String environment,
            List<MethodInfo> onMessageMethods, List<MethodInfo> onResponseMethods) {

        var adaptor = new GeneratedBeanGizmoAdaptor(producer);

        try (ClassCreator creator = ClassCreator.builder()
                .classOutput(adaptor)
                .className(generatedName)
                .build()) {

            // Add JAX-RS @Path annotation
            creator.addAnnotation("jakarta.ws.rs.Path").addValue("value", pathValue);
            // Make it a CDI bean
            creator.addAnnotation("jakarta.enterprise.context.ApplicationScoped");

            // Inject EddiClient
            FieldCreator eddiClientField = creator.getFieldCreator("eddiClient", EddiClient.class);
            eddiClientField.addAnnotation("jakarta.inject.Inject");

            // Inject the annotated class (for hook invocation)
            FieldCreator hookBeanField = creator.getFieldCreator("hookBean", annotatedClass.name().toString());
            hookBeanField.addAnnotation("jakarta.inject.Inject");

            // Per-user conversation map (B2 fix: reuse conversations)
            FieldCreator conversationsField = creator.getFieldCreator("conversations", ConcurrentHashMap.class);

            // Generate init method to initialize the map
            generateInitMethod(creator);

            // Generate POST method for say
            generateSayMethod(creator, annotatedClass, agentId, environment,
                    onMessageMethods, onResponseMethods);

            // Generate SSE streaming POST if enabled
            if (streaming) {
                generateStreamMethod(creator, agentId, environment);
            }

            LOG.infof("Generated @EddiAgent resource: %s with %d @OnMessage and %d @OnResponse hooks",
                    generatedName, onMessageMethods.size(), onResponseMethods.size());
        }
    }

    private void generateInitMethod(ClassCreator creator) {
        try (MethodCreator method = creator.getMethodCreator("init", void.class)) {
            method.addAnnotation("jakarta.annotation.PostConstruct");

            // this.conversations = new ConcurrentHashMap<>();
            ResultHandle map = method.newInstance(MethodDescriptor.ofConstructor(ConcurrentHashMap.class));
            method.writeInstanceField(
                    FieldDescriptor.of(creator.getClassName(), "conversations", ConcurrentHashMap.class),
                    method.getThis(), map);
            method.returnVoid();
        }
    }

    private void generateSayMethod(ClassCreator creator, ClassInfo annotatedClass,
            String agentId, String environment,
            List<MethodInfo> onMessageMethods, List<MethodInfo> onResponseMethods) {

        try (MethodCreator method = creator.getMethodCreator("handleMessage",
                ConversationResult.class, String.class, String.class)) {
            method.addAnnotation("jakarta.ws.rs.POST");
            method.addAnnotation("jakarta.ws.rs.Consumes").addValue("value", "text/plain");
            method.addAnnotation("jakarta.ws.rs.Produces").addValue("value", "application/json");

            // Add @QueryParam("userId") to the second parameter
            method.getParameterAnnotations(1).addAnnotation("jakarta.ws.rs.QueryParam")
                    .addValue("value", "userId");
            method.getParameterAnnotations(1).addAnnotation("jakarta.ws.rs.DefaultValue")
                    .addValue("value", "anonymous");

            ResultHandle message = method.getMethodParam(0);
            ResultHandle userId = method.getMethodParam(1);

            // --- Invoke @OnMessage hooks before sending (D5 fix) ---
            ResultHandle hookBean = method.readInstanceField(
                    FieldDescriptor.of(creator.getClassName(), "hookBean", annotatedClass.name().toString()),
                    method.getThis());

            for (MethodInfo hookMethod : onMessageMethods) {
                if (hookMethod.parametersCount() == 1
                        && hookMethod.parameterType(0).name().toString().equals("java.lang.String")) {
                    // hook takes (String message), may return modified String
                    if (hookMethod.returnType().name().toString().equals("java.lang.String")) {
                        message = method.invokeVirtualMethod(
                                MethodDescriptor.ofMethod(annotatedClass.name().toString(),
                                        hookMethod.name(), String.class, String.class),
                                hookBean, message);
                    } else {
                        // void hook
                        method.invokeVirtualMethod(
                                MethodDescriptor.ofMethod(annotatedClass.name().toString(),
                                        hookMethod.name(), void.class, String.class),
                                hookBean, message);
                    }
                }
            }

            // --- Get or create conversation (B2 fix: reuse per userId) ---
            ResultHandle conversations = method.readInstanceField(
                    FieldDescriptor.of(creator.getClassName(), "conversations", ConcurrentHashMap.class),
                    method.getThis());

            // Use AssignableResultHandle so we can assign in both branches
            AssignableResultHandle conv = method.createVariable(Conversation.class);

            // Conversation existing = conversations.get(userId);
            ResultHandle existing = method.invokeVirtualMethod(
                    MethodDescriptor.ofMethod(ConcurrentHashMap.class, "get", Object.class, Object.class),
                    conversations, userId);

            // if (existing == null) { create new } else { reuse }
            BranchResult branch = method.ifNull(existing);
            BytecodeCreator trueBranch = branch.trueBranch();
            BytecodeCreator falseBranch = branch.falseBranch();

            // True branch: create new conversation
            ResultHandle eddiClient = trueBranch.readInstanceField(
                    FieldDescriptor.of(creator.getClassName(), "eddiClient", EddiClient.class),
                    trueBranch.getThis());
            ResultHandle agentBuilder = trueBranch.invokeVirtualMethod(
                    MethodDescriptor.ofMethod(EddiClient.class, "agent", EddiClient.AgentBuilder.class, String.class),
                    eddiClient, trueBranch.load(agentId));
            if (environment != null && !environment.isEmpty()) {
                agentBuilder = trueBranch.invokeVirtualMethod(
                        MethodDescriptor.ofMethod(EddiClient.AgentBuilder.class, "environment",
                                EddiClient.AgentBuilder.class, String.class),
                        agentBuilder, trueBranch.load(environment));
            }
            ResultHandle newConv = trueBranch.invokeVirtualMethod(
                    MethodDescriptor.ofMethod(EddiClient.AgentBuilder.class, "startConversation", Conversation.class),
                    agentBuilder);
            trueBranch.invokeVirtualMethod(
                    MethodDescriptor.ofMethod(ConcurrentHashMap.class, "put", Object.class, Object.class, Object.class),
                    conversations, userId, newConv);
            trueBranch.assign(conv, newConv);

            // False branch: reuse existing conversation
            falseBranch.assign(conv, falseBranch.checkCast(existing, Conversation.class));

            // conv.say(message)
            ResultHandle result = method.invokeVirtualMethod(
                    MethodDescriptor.ofMethod(Conversation.class, "say", ConversationResult.class, String.class),
                    conv, message);

            // --- Invoke @OnResponse hooks after receiving (D5 fix) ---
            for (MethodInfo hookMethod : onResponseMethods) {
                if (hookMethod.parametersCount() == 1
                        && hookMethod.parameterType(0).name().toString().equals(ConversationResult.class.getName())) {
                    if (hookMethod.returnType().name().toString().equals(ConversationResult.class.getName())) {
                        result = method.invokeVirtualMethod(
                                MethodDescriptor.ofMethod(annotatedClass.name().toString(),
                                        hookMethod.name(), ConversationResult.class, ConversationResult.class),
                                hookBean, result);
                    } else {
                        method.invokeVirtualMethod(
                                MethodDescriptor.ofMethod(annotatedClass.name().toString(),
                                        hookMethod.name(), void.class, ConversationResult.class),
                                hookBean, result);
                    }
                }
            }

            method.returnValue(result);
        }
    }

    private void generateStreamMethod(ClassCreator creator, String agentId, String environment) {
        try (MethodCreator method = creator.getMethodCreator("handleMessageStream",
                io.smallrye.mutiny.Multi.class, String.class, String.class)) {
            method.addAnnotation("jakarta.ws.rs.POST");
            method.addAnnotation("jakarta.ws.rs.Path").addValue("value", "/stream");
            method.addAnnotation("jakarta.ws.rs.Consumes").addValue("value", "text/plain");
            method.addAnnotation("jakarta.ws.rs.Produces").addValue("value", "text/event-stream");

            // Add @QueryParam("userId") to the second parameter
            method.getParameterAnnotations(1).addAnnotation("jakarta.ws.rs.QueryParam")
                    .addValue("value", "userId");
            method.getParameterAnnotations(1).addAnnotation("jakarta.ws.rs.DefaultValue")
                    .addValue("value", "anonymous");

            ResultHandle userId = method.getMethodParam(1);

            // Get or create conversation (reuse same map)
            ResultHandle conversations = method.readInstanceField(
                    FieldDescriptor.of(creator.getClassName(), "conversations", ConcurrentHashMap.class),
                    method.getThis());

            AssignableResultHandle conv = method.createVariable(Conversation.class);

            ResultHandle existing = method.invokeVirtualMethod(
                    MethodDescriptor.ofMethod(ConcurrentHashMap.class, "get", Object.class, Object.class),
                    conversations, userId);

            BranchResult branch = method.ifNull(existing);
            BytecodeCreator trueBranch = branch.trueBranch();
            BytecodeCreator falseBranch = branch.falseBranch();

            ResultHandle eddiClient = trueBranch.readInstanceField(
                    FieldDescriptor.of(creator.getClassName(), "eddiClient", EddiClient.class),
                    trueBranch.getThis());
            ResultHandle agentBuilder = trueBranch.invokeVirtualMethod(
                    MethodDescriptor.ofMethod(EddiClient.class, "agent", EddiClient.AgentBuilder.class, String.class),
                    eddiClient, trueBranch.load(agentId));
            if (environment != null && !environment.isEmpty()) {
                agentBuilder = trueBranch.invokeVirtualMethod(
                        MethodDescriptor.ofMethod(EddiClient.AgentBuilder.class, "environment",
                                EddiClient.AgentBuilder.class, String.class),
                        agentBuilder, trueBranch.load(environment));
            }
            ResultHandle newConv = trueBranch.invokeVirtualMethod(
                    MethodDescriptor.ofMethod(EddiClient.AgentBuilder.class, "startConversation", Conversation.class),
                    agentBuilder);
            trueBranch.invokeVirtualMethod(
                    MethodDescriptor.ofMethod(ConcurrentHashMap.class, "put", Object.class, Object.class, Object.class),
                    conversations, userId, newConv);
            trueBranch.assign(conv, newConv);

            // False branch: reuse existing
            falseBranch.assign(conv, falseBranch.checkCast(existing, Conversation.class));

            ResultHandle message = method.getMethodParam(0);
            ResultHandle stream = method.invokeVirtualMethod(
                    MethodDescriptor.ofMethod(Conversation.class, "sayStreaming",
                            io.smallrye.mutiny.Multi.class, String.class),
                    conv, message);

            method.returnValue(stream);
        }
    }
}
