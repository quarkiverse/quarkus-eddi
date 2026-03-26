package io.quarkiverse.eddi.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.util.Optional;

/**
 * Configuration for the Quarkus EDDI extension.
 * <p>
 * All properties are under the {@code quarkus.eddi} namespace.
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
     * Connect timeout for REST client calls.
     */
    @WithDefault("5000")
    int connectTimeoutMs();

    /**
     * Read timeout for REST client calls.
     */
    @WithDefault("30000")
    int readTimeoutMs();

    /**
     * Dev Services configuration.
     */
    DevServicesConfig devservices();

    /**
     * MCP Bridge configuration.
     */
    McpBridgeConfig mcpBridge();

    /**
     * Dev Services sub-configuration.
     */
    interface DevServicesConfig {

        /**
         * Whether Dev Services should be enabled. Defaults to true in dev/test mode.
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
}
