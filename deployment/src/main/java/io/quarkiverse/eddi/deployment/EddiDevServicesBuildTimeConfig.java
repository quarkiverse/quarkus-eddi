package io.quarkiverse.eddi.deployment;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * Build-time configuration for EDDI Dev Services.
 * <p>
 * <b>Note:</b> This interface is <i>not injected</i> into build steps. Quarkus does not
 * support {@code @ConfigMapping} injection in deployment build steps. The actual
 * config values are read via {@code ConfigProvider.getConfig()} in
 * {@link EddiDevServicesProcessor}. This interface serves as the canonical
 * documentation of the available properties and their defaults.
 *
 * @see EddiDevServicesProcessor
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

    /**
     * Whether the EDDI Dev Service containers are shared across Quarkus apps.
     * <p>
     * When shared, containers are reused across live reloads and multiple
     * dev-mode applications using the same {@code serviceName}.
     */
    @WithDefault("true")
    boolean shared();

    /**
     * Service name used to identify shared EDDI Dev Service containers.
     * <p>
     * Only applies when {@code shared=true}.
     */
    @WithDefault("eddi")
    String serviceName();
}
