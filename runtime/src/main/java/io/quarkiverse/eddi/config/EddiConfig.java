package io.quarkiverse.eddi.config;

import java.time.Duration;
import java.util.Optional;

import io.quarkus.runtime.annotations.ConfigPhase;
import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * Configuration for the Quarkus EDDI extension.
 * <p>
 * All properties are under the {@code quarkus.eddi} namespace.
 * <p>
 * Dev Services configuration is handled separately at build time via
 * {@code EddiDevServicesBuildTimeConfig} in the deployment module.
 */
@ConfigMapping(prefix = "quarkus.eddi")
@ConfigRoot(phase = ConfigPhase.RUN_TIME)
public interface EddiConfig {

    /**
     * The base URL of the EDDI server. It is also the default for
     * {@code quarkus.rest-client.eddi.url}, which the REST clients use.
     * Auto-configured by Dev Services in dev and test mode.
     */
    @WithDefault("http://localhost:7070")
    String url();

    /**
     * Timeout of the blocking convenience methods (e.g. {@code say()}, {@code discuss()}).
     * Group discussions run all member turns before the server answers, so keep it generous.
     * The HTTP read timeout is set separately via {@code quarkus.rest-client.eddi.read-timeout}
     * (default 300000 ms).
     */
    @WithDefault("5M")
    Duration timeout();

    /**
     * Optional API key for authenticating with the EDDI server.
     */
    Optional<String> apiKey();

    /**
     * Default environment for agent operations.
     */
    @WithDefault("production")
    String environment();

    /**
     * Health check configuration.
     */
    HealthConfig health();

    /**
     * Health check sub-configuration.
     */
    interface HealthConfig {

        /**
         * Whether the EDDI readiness health check is enabled.
         */
        @WithDefault("true")
        boolean enabled();
    }
}
