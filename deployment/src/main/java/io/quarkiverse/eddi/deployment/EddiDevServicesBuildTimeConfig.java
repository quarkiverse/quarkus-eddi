package io.quarkiverse.eddi.deployment;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * Build-time configuration for EDDI Dev Services.
 * <p>
 * This is a separate config mapping from the runtime config because
 * Dev Services runs at build time and cannot access runtime config directly.
 */
@ConfigMapping(prefix = "quarkus.eddi.devservices")
public interface EddiDevServicesBuildTimeConfig {

    /**
     * Whether Dev Services should be enabled.
     */
    @WithDefault("true")
    boolean enabled();

    /**
     * Docker image for the EDDI server.
     */
    @WithDefault("labsai/eddi:6")
    String image();

    /**
     * Docker image for MongoDB.
     */
    @WithDefault("mongo:6.0")
    String mongodbImage();

    /**
     * Whether to seed a demo agent on startup.
     */
    @WithDefault("false")
    boolean seedDemoAgent();
}
