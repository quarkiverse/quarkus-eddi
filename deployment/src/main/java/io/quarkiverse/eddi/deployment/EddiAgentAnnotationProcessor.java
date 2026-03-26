package io.quarkiverse.eddi.deployment;

import io.quarkiverse.eddi.annotations.EddiAgent;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.DotName;
import org.jboss.logging.Logger;

import java.util.List;

/**
 * Build step processor that scans for {@link EddiAgent} annotations
 * and generates JAX-RS endpoint resources at build time.
 * <p>
 * For each class annotated with {@code @EddiAgent}, this processor generates
 * a JAX-RS resource that proxies requests to the specified EDDI agent.
 */
public class EddiAgentAnnotationProcessor {

    private static final Logger LOG = Logger.getLogger(EddiAgentAnnotationProcessor.class);
    private static final DotName EDDI_AGENT = DotName.createSimple(EddiAgent.class.getName());

    @BuildStep
    void processEddiAgentAnnotations(CombinedIndexBuildItem combinedIndex,
            List<AdditionalBeanBuildItem> additionalBeans) {

        var annotations = combinedIndex.getIndex().getAnnotations(EDDI_AGENT);

        for (AnnotationInstance annotation : annotations) {
            String agentId = annotation.value("id").asString();
            String path = annotation.value("path").asString();
            boolean streaming = annotation.value("streaming") != null
                    && annotation.value("streaming").asBoolean();

            String className = annotation.target().asClass().name().toString();

            LOG.infof("Discovered @EddiAgent: %s → agent=%s, path=%s, streaming=%s",
                    className, agentId, path, streaming);

            // TODO: Phase 2 — Generate JAX-RS resource class via bytecode generation
            // that creates endpoints at the specified path, proxying to EDDI via EddiClient.
            // The generated resource will:
            // 1. Create/reuse conversations per user
            // 2. Invoke @OnMessage hooks before sending to EDDI
            // 3. Invoke @OnResponse hooks after receiving the response
            // 4. Optionally expose an SSE streaming endpoint
        }
    }
}
