package io.quarkiverse.eddi.deployment;

import java.util.List;

import org.jboss.jandex.*;
import org.jboss.logging.Logger;

import io.quarkiverse.eddi.BoundedConversationMap;
import io.quarkiverse.eddi.Conversation;
import io.quarkiverse.eddi.EddiClient;
import io.quarkiverse.eddi.EddiDefaults;
import io.quarkiverse.eddi.annotations.EddiAgent;
import io.quarkiverse.eddi.annotations.OnMessage;
import io.quarkiverse.eddi.annotations.OnResponse;
import io.quarkiverse.eddi.model.ConversationResult;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import io.quarkus.gizmo.*;
import io.quarkus.resteasy.reactive.spi.GeneratedJaxRsResourceBuildItem;
import io.quarkus.resteasy.reactive.spi.GeneratedJaxRsResourceGizmoAdaptor;

/**
 * Build step processor that scans for {@link EddiAgent} annotations and generates
 * JAX-RS endpoint resources at build time using Gizmo bytecode generation.
 * <p>
 * For each class annotated with {@code @EddiAgent}, this processor generates
 * a JAX-RS resource that:
 * <ol>
 * <li>Manages a per-user conversation map using {@link BoundedConversationMap} with LRU eviction</li>
 * <li>Enforces a maximum map size ({@link EddiDefaults#MAX_CONVERSATIONS_PER_AGENT}) to prevent memory leaks</li>
 * <li>Invokes {@code @OnMessage} hooks before sending to EDDI (both say and stream)</li>
 * <li>Invokes {@code @OnResponse} hooks after receiving the response</li>
 * <li>Optionally exposes an SSE streaming endpoint when {@code streaming = true}</li>
 * <li>Clears the local conversation cache on bean destruction via {@code @PreDestroy}.
 * Note: EDDI conversations persist on the server across client restarts by design,
 * so server-side cleanup is intentionally not performed.</li>
 * </ol>
 */
public class EddiAgentAnnotationProcessor {

    private static final Logger LOG = Logger.getLogger(EddiAgentAnnotationProcessor.class);
    private static final DotName EDDI_AGENT = DotName.createSimple(EddiAgent.class.getName());
    private static final DotName ON_MESSAGE = DotName.createSimple(OnMessage.class.getName());
    private static final DotName ON_RESPONSE = DotName.createSimple(OnResponse.class.getName());

    @BuildStep
    void processEddiAgentAnnotations(CombinedIndexBuildItem combinedIndex,
            BuildProducer<GeneratedJaxRsResourceBuildItem> generatedResourceProducer,
            BuildProducer<AdditionalBeanBuildItem> additionalBeans) {

        var index = combinedIndex.getIndex();
        var annotations = index.getAnnotations(EDDI_AGENT);

        for (AnnotationInstance annotation : annotations) {
            ClassInfo classInfo = annotation.target().asClass();
            String agentId = annotation.value("id").asString();
            String pathValue = annotation.value("path").asString();
            // valueWithDefault: annotation.value(...) is null for defaulted members
            boolean streaming = annotation.valueWithDefault(index, "streaming").asBoolean();
            String environment = annotation.valueWithDefault(index, "environment").asString();

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
            validateHooks(onMessageMethods, "@OnMessage", String.class.getName());
            validateHooks(onResponseMethods, "@OnResponse", ConversationResult.class.getName());

            generateResourceClass(generatedResourceProducer, generatedName, classInfo,
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

    /**
     * Hooks must take exactly one parameter of {@code paramType} and return either
     * {@code void} or {@code paramType}. Fail the build instead of silently skipping.
     */
    private static void validateHooks(List<MethodInfo> hooks, String annotation, String paramType) {
        for (MethodInfo hook : hooks) {
            String returnType = hook.returnType().name().toString();
            boolean validParams = hook.parametersCount() == 1
                    && hook.parameterType(0).name().toString().equals(paramType);
            boolean validReturn = returnType.equals("void") || returnType.equals(paramType);
            if (!validParams || !validReturn || !java.lang.reflect.Modifier.isPublic(hook.flags())
                    || java.lang.reflect.Modifier.isStatic(hook.flags())) {
                throw new IllegalStateException(String.format(
                        "%s method %s.%s must be public, non-static and have the signature `void|%s name(%s)`",
                        annotation, hook.declaringClass().name(), hook.name(), paramType, paramType));
            }
        }
    }

    private void generateResourceClass(BuildProducer<GeneratedJaxRsResourceBuildItem> producer,
            String generatedName, ClassInfo annotatedClass,
            String agentId, String pathValue, boolean streaming, String environment,
            List<MethodInfo> onMessageMethods, List<MethodInfo> onResponseMethods) {

        // Emit via Quarkus REST's generated-resource SPI (not GeneratedBeanBuildItem):
        // only then does Quarkus REST index the class as a JAX-RS resource and
        // register it as a bean, like Panache REST Data does for its resources.
        var adaptor = new GeneratedJaxRsResourceGizmoAdaptor(producer);

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
            // Package-private: ArC's generated _Bean class injects fields directly
            // and cannot write to private fields of a generated class.
            eddiClientField.setModifiers(0);
            eddiClientField.addAnnotation("jakarta.inject.Inject");

            // Inject the annotated class (for hook invocation)
            FieldCreator hookBeanField = creator.getFieldCreator("hookBean", annotatedClass.name().toString());
            hookBeanField.setModifiers(0);
            hookBeanField.addAnnotation("jakarta.inject.Inject");

            // Per-user conversation map with LRU eviction
            creator.getFieldCreator("conversations", BoundedConversationMap.class).setModifiers(0);

            // Generate init method to initialize the map
            generateInitMethod(creator);

            // Generate cleanup method to clear local cache on shutdown
            generateDestroyMethod(creator);

            // Generate POST method for say
            generateSayMethod(creator, annotatedClass, agentId, environment,
                    onMessageMethods, onResponseMethods);

            // Generate SSE streaming POST if enabled
            if (streaming) {
                generateStreamMethod(creator, annotatedClass, agentId, environment, onMessageMethods);
            }

            LOG.infof("Generated @EddiAgent resource: %s with %d @OnMessage and %d @OnResponse hooks",
                    generatedName, onMessageMethods.size(), onResponseMethods.size());
        }
    }

    private void generateInitMethod(ClassCreator creator) {
        try (MethodCreator method = creator.getMethodCreator("init", void.class)) {
            method.addAnnotation("jakarta.annotation.PostConstruct");

            // this.conversations = new BoundedConversationMap(MAX_CONVERSATIONS_PER_AGENT);
            ResultHandle maxSize = method.load(EddiDefaults.MAX_CONVERSATIONS_PER_AGENT);
            ResultHandle map = method.newInstance(
                    MethodDescriptor.ofConstructor(BoundedConversationMap.class, int.class),
                    maxSize);
            method.writeInstanceField(
                    FieldDescriptor.of(creator.getClassName(), "conversations", BoundedConversationMap.class),
                    method.getThis(), map);
            method.returnVoid();
        }
    }

    /**
     * Generate a @PreDestroy method that clears the local conversation cache.
     * <p>
     * Note: EDDI conversations persist on the server across client restarts
     * by design, so we intentionally do NOT end server-side conversations here.
     * This only releases the local in-memory references.
     */
    private void generateDestroyMethod(ClassCreator creator) {
        try (MethodCreator method = creator.getMethodCreator("destroy", void.class)) {
            method.addAnnotation("jakarta.annotation.PreDestroy");

            ResultHandle conversations = method.readInstanceField(
                    FieldDescriptor.of(creator.getClassName(), "conversations", BoundedConversationMap.class),
                    method.getThis());

            method.invokeVirtualMethod(
                    MethodDescriptor.ofMethod(BoundedConversationMap.class, "clear", void.class),
                    conversations);
            method.returnVoid();
        }
    }

    private void generateSayMethod(ClassCreator creator, ClassInfo annotatedClass,
            String agentId, String environment,
            List<MethodInfo> onMessageMethods, List<MethodInfo> onResponseMethods) {

        try (MethodCreator method = creator.getMethodCreator("handleMessage",
                ConversationResult.class, String.class, String.class)) {
            method.addAnnotation("jakarta.ws.rs.POST");
            method.addAnnotation("jakarta.ws.rs.Consumes").addValue("value", new String[] { "text/plain" });
            method.addAnnotation("jakarta.ws.rs.Produces").addValue("value", new String[] { "application/json" });

            // Add @QueryParam("userId") to the second parameter
            method.getParameterAnnotations(1).addAnnotation("jakarta.ws.rs.QueryParam")
                    .addValue("value", "userId");
            method.getParameterAnnotations(1).addAnnotation("jakarta.ws.rs.DefaultValue")
                    .addValue("value", "anonymous");

            ResultHandle message = method.getMethodParam(0);
            ResultHandle userId = method.getMethodParam(1);

            // --- Invoke @OnMessage hooks before sending ---
            ResultHandle hookBean = method.readInstanceField(
                    FieldDescriptor.of(creator.getClassName(), "hookBean", annotatedClass.name().toString()),
                    method.getThis());

            message = invokeOnMessageHooks(method, hookBean, annotatedClass, onMessageMethods, message);

            // --- Get or create conversation via BoundedConversationMap ---
            ResultHandle conv = getOrCreateConversation(method, creator, agentId, environment, userId);

            // conv.say(message)
            ResultHandle result = method.invokeVirtualMethod(
                    MethodDescriptor.ofMethod(Conversation.class, "say", ConversationResult.class, String.class),
                    conv, message);

            // --- Invoke @OnResponse hooks after receiving ---
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

    private void generateStreamMethod(ClassCreator creator, ClassInfo annotatedClass,
            String agentId, String environment, List<MethodInfo> onMessageMethods) {
        try (MethodCreator method = creator.getMethodCreator("handleMessageStream",
                io.smallrye.mutiny.Multi.class, String.class, String.class)) {
            method.addAnnotation("jakarta.ws.rs.POST");
            method.addAnnotation("jakarta.ws.rs.Path").addValue("value", "/stream");
            // A Multi return type would run on the event loop, but starting the
            // conversation (and user hooks) blocks — run on a worker thread.
            method.addAnnotation("io.smallrye.common.annotation.Blocking");
            method.addAnnotation("jakarta.ws.rs.Consumes").addValue("value", new String[] { "text/plain" });
            method.addAnnotation("jakarta.ws.rs.Produces").addValue("value", new String[] { "text/event-stream" });

            // Add @QueryParam("userId") to the second parameter
            method.getParameterAnnotations(1).addAnnotation("jakarta.ws.rs.QueryParam")
                    .addValue("value", "userId");
            method.getParameterAnnotations(1).addAnnotation("jakarta.ws.rs.DefaultValue")
                    .addValue("value", "anonymous");

            ResultHandle message = method.getMethodParam(0);
            ResultHandle userId = method.getMethodParam(1);

            // --- Invoke @OnMessage hooks before sending (parity with say) ---
            ResultHandle hookBean = method.readInstanceField(
                    FieldDescriptor.of(creator.getClassName(), "hookBean", annotatedClass.name().toString()),
                    method.getThis());

            message = invokeOnMessageHooks(method, hookBean, annotatedClass, onMessageMethods, message);

            // --- Get or create conversation via BoundedConversationMap ---
            ResultHandle conv = getOrCreateConversation(method, creator, agentId, environment, userId);

            ResultHandle stream = method.invokeVirtualMethod(
                    MethodDescriptor.ofMethod(Conversation.class, "sayStreaming",
                            io.smallrye.mutiny.Multi.class, String.class),
                    conv, message);

            method.returnValue(stream);
        }
    }

    /**
     * Invoke @OnMessage hooks on the message, returning the (possibly transformed) message.
     */
    private ResultHandle invokeOnMessageHooks(MethodCreator method, ResultHandle hookBean,
            ClassInfo annotatedClass, List<MethodInfo> onMessageMethods, ResultHandle message) {
        for (MethodInfo hookMethod : onMessageMethods) {
            if (hookMethod.parametersCount() == 1
                    && hookMethod.parameterType(0).name().toString().equals("java.lang.String")) {
                if (hookMethod.returnType().name().toString().equals("java.lang.String")) {
                    message = method.invokeVirtualMethod(
                            MethodDescriptor.ofMethod(annotatedClass.name().toString(),
                                    hookMethod.name(), String.class, String.class),
                            hookBean, message);
                } else {
                    method.invokeVirtualMethod(
                            MethodDescriptor.ofMethod(annotatedClass.name().toString(),
                                    hookMethod.name(), void.class, String.class),
                            hookBean, message);
                }
            }
        }
        return message;
    }

    /**
     * Generate bytecode for {@code conversations.conversationFor(userId, eddiClient, agentId, environment)}:
     * the user's conversation, started with that userId on first use.
     */
    private ResultHandle getOrCreateConversation(MethodCreator method, ClassCreator creator,
            String agentId, String environment, ResultHandle userId) {

        ResultHandle conversations = method.readInstanceField(
                FieldDescriptor.of(creator.getClassName(), "conversations", BoundedConversationMap.class),
                method.getThis());
        ResultHandle eddiClient = method.readInstanceField(
                FieldDescriptor.of(creator.getClassName(), "eddiClient", EddiClient.class),
                method.getThis());

        return method.invokeVirtualMethod(
                MethodDescriptor.ofMethod(BoundedConversationMap.class, "conversationFor", Conversation.class,
                        String.class, EddiClient.class, String.class, String.class),
                conversations, userId, eddiClient, method.load(agentId),
                method.load(environment == null ? "" : environment));
    }
}
