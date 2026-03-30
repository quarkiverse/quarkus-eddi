package io.quarkiverse.eddi.deployment;

import io.quarkiverse.eddi.EddiClient;
import io.quarkiverse.eddi.EddiHealthCheck;
import io.quarkiverse.eddi.client.EddiApiKeyFilter;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;

/**
 * Core build step processor for the Quarkus EDDI extension.
 * <p>
 * Registers the extension feature and CDI beans for the SDK facade layer.
 * <p>
 * Note: REST client interfaces (annotated with {@code @RegisterRestClient})
 * are <em>not</em> registered here — Quarkus discovers them automatically.
 * Registering them as {@link AdditionalBeanBuildItem} would create conflicting
 * bean definitions.
 */
public class EddiProcessor {

    private static final String FEATURE = "eddi";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    AdditionalBeanBuildItem registerBeans() {
        return AdditionalBeanBuildItem.builder()
                .addBeanClasses(
                        EddiClient.class,
                        EddiHealthCheck.class,
                        EddiApiKeyFilter.class)
                .setUnremovable()
                .build();
    }
}
