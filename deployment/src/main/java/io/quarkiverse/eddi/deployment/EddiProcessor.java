package io.quarkiverse.eddi.deployment;

import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;

/**
 * Core build step processor for the Quarkus EDDI extension.
 * <p>
 * Registers the extension feature and CDI beans.
 */
public class EddiProcessor {

    private static final String FEATURE = "eddi";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }
}
