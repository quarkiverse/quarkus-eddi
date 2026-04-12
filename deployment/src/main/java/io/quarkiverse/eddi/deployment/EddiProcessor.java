package io.quarkiverse.eddi.deployment;

import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.IndexDependencyBuildItem;
import io.quarkus.deployment.builditem.RunTimeConfigBuilderBuildItem;
import io.quarkus.deployment.builditem.StaticInitConfigBuilderBuildItem;

/**
 * Core build step processor for the Quarkus EDDI extension.
 * <p>
 * Registers the extension feature, CDI beans, ConfigMapping, and ensures the
 * runtime module is included in the Jandex index for REST client and config
 * mapping discovery.
 */
public class EddiProcessor {

    private static final String FEATURE = "eddi";
    private static final String CONFIG_BUILDER_FQN = "io.quarkiverse.eddi.config.EddiConfigBuilderCustomizer";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    /**
     * Ensure the runtime module's classes are included in the Jandex index.
     * This is required for Quarkus to discover:
     * <ul>
     * <li>{@code @RegisterRestClient} interfaces (all 8 REST clients)</li>
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
     * Register the EddiConfig {@code @ConfigMapping} for static init and runtime
     * so SmallRye Config recognizes all {@code quarkus.eddi.*} properties.
     * Without this, users get "unrecognized configuration key" warnings.
     * <p>
     * The customizer class lives in the <b>runtime</b> module and is referenced by
     * FQN string to avoid classloader boundary issues between deployment and runtime.
     *
     * @see io.quarkiverse.eddi.config.EddiConfigBuilderCustomizer
     */
    @BuildStep
    StaticInitConfigBuilderBuildItem registerStaticInitConfigMapping() {
        return new StaticInitConfigBuilderBuildItem(CONFIG_BUILDER_FQN);
    }

    @BuildStep
    RunTimeConfigBuilderBuildItem registerRuntimeConfigMapping() {
        return new RunTimeConfigBuilderBuildItem(CONFIG_BUILDER_FQN);
    }
}
