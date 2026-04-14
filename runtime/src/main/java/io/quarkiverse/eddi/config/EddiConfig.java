package io.quarkiverse.eddi.config;

import java.util.Optional;

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
public interface EddiConfig {

    /**
     * The base URL of the EDDI server.
     * Auto-configured by Dev Services when running in dev mode.
     */
    @WithDefault("http://localhost:7070")
    String url();

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
     * MCP Bridge configuration.
     */
    McpBridgeConfig mcpBridge();

    /**
     * Health check configuration.
     */
    HealthConfig health();

    /**
     * MCP Tool Bridge sub-configuration.
     */
    interface McpBridgeConfig {

        /**
         * Whether the MCP tool bridge is enabled.
         */
        @WithDefault("true")
        boolean enabled();

        /**
         * Comma-separated agent IDs that should discover bridge tools.
         * Use "*" for all agents.
         */
        @WithDefault("*")
        String agents();
    }

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
