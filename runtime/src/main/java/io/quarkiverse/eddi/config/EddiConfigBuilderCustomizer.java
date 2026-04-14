package io.quarkiverse.eddi.config;

import io.quarkus.runtime.configuration.ConfigBuilder;
import io.smallrye.config.SmallRyeConfigBuilder;

/**
 * Runtime config builder that registers the {@link EddiConfig}
 * {@code @ConfigMapping} with SmallRye Config.
 * <p>
 * Referenced by name from the deployment module's
 * {@code RunTimeConfigBuilderBuildItem} so that Quarkus recognizes all
 * {@code quarkus.eddi.*} properties and stops emitting
 * "unrecognized configuration key" warnings.
 */
public class EddiConfigBuilderCustomizer implements ConfigBuilder {

    @Override
    public SmallRyeConfigBuilder configBuilder(SmallRyeConfigBuilder builder) {
        builder.withMapping(EddiConfig.class);
        return builder;
    }
}
