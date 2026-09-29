package io.quarkiverse.eddi.deployment;

import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.IndexDependencyBuildItem;
import io.quarkus.deployment.builditem.RunTimeConfigurationDefaultBuildItem;

/**
 * Core build step processor for the Quarkus EDDI extension.
 * <p>
 * Registers the extension feature, CDI beans and REST client defaults, and ensures the
 * runtime module is included in the Jandex index for REST client and config
 * mapping discovery.
 */
public class EddiProcessor {

    private static final String FEATURE = "eddi";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    /**
     * Ensure the runtime module's classes are included in the Jandex index.
     * This is required for Quarkus to discover:
     * <ul>
     * <li>{@code @RegisterRestClient} interfaces (all 9 REST clients)</li>
     * <li>{@code @ConfigMapping} interfaces (EddiConfig)</li>
     * <li>{@code @ApplicationScoped} beans (EddiClient, EddiHealthCheck)</li>
     * </ul>
     */
    @BuildStep
    IndexDependencyBuildItem indexRuntimeModule() {
        return new IndexDependencyBuildItem("io.quarkiverse.eddi", "quarkus-eddi");
    }

    @BuildStep
    AdditionalBeanBuildItem registerBeans() {
        return AdditionalBeanBuildItem.builder()
                .addBeanClasses(
                        "io.quarkiverse.eddi.EddiClient",
                        "io.quarkiverse.eddi.EddiHealthCheck",
                        "io.quarkiverse.eddi.client.EddiApiKeyFilter")
                .setUnremovable()
                .build();
    }

    /**
     * The REST clients read {@code quarkus.rest-client.eddi.*}. Default their URL to
     * {@code quarkus.eddi.url} so that the one documented property is enough, and
     * raise the 30 s Quarkus read timeout: group discussions and slow agent turns
     * only answer once all LLM calls are done.
     */
    @BuildStep
    void restClientDefaults(BuildProducer<RunTimeConfigurationDefaultBuildItem> defaults) {
        defaults.produce(new RunTimeConfigurationDefaultBuildItem("quarkus.rest-client.eddi.url", "${quarkus.eddi.url}"));
        defaults.produce(new RunTimeConfigurationDefaultBuildItem("quarkus.rest-client.eddi.read-timeout", "300000"));
    }
}
