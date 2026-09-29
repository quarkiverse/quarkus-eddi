package io.quarkiverse.eddi.deployment;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * Build-time configuration for EDDI Dev Services.
 * <p>
 * Dev Services start an EDDI server plus MongoDB in dev and test mode unless
 * disabled here or globally ({@code quarkus.devservices.enabled=false}), or unless
 * {@code quarkus.eddi.url} / {@code quarkus.rest-client.eddi.url} is configured.
 */
@ConfigMapping(prefix = "quarkus.eddi.devservices")
@ConfigRoot(phase = ConfigPhase.BUILD_TIME)
public interface EddiDevServicesBuildTimeConfig {

    /**
     * Whether Dev Services should be enabled.
     */
    @WithDefault("true")
    boolean enabled();

    /**
     * Docker image for the EDDI server. Keep aligned with the EDDI release this SDK targets.
     */
    @WithDefault("labsai/eddi:6.4.0")
    String image();

    /**
     * Docker image for MongoDB.
     */
    @WithDefault("mongo:7.0.14")
    String mongodbImage();
}
