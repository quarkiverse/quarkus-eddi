package io.quarkiverse.eddi.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request object for creating an API agent from an OpenAPI specification.
 * <p>
 * Matches EDDI v6's {@code CreateApiAgentRequest} record. Maps to
 * {@code POST /administration/agents/setup-api}.
 */
public record CreateApiAgentRequest(
        @JsonProperty(required = true) String name,
        @JsonProperty(required = true) String systemPrompt,
        @JsonProperty(required = true) String openApiSpec,
        String provider,
        String model,
        String apiKey,
        String apiBaseUrl,
        String apiAuth,
        String endpoints,
        Boolean enableQuickReplies,
        Boolean enableSentimentAnalysis,
        Boolean deploy,
        String environment) {

    public static Builder<? extends Builder<?>> builder() {
        return new Builder<>();
    }

    /**
     * Fluent builder with self-referential generics for clean subclass chaining.
     *
     * @param <T> the concrete builder type (enables subclass setters to return the right type)
     */
    @SuppressWarnings("unchecked")
    public static class Builder<T extends Builder<T>> {
        private String name;
        private String systemPrompt;
        private String openApiSpec;
        private String provider;
        private String model;
        private String apiKey;
        private String apiBaseUrl;
        private String apiAuth;
        private String endpoints;
        private Boolean enableQuickReplies;
        private Boolean enableSentimentAnalysis;
        private Boolean deploy;
        private String environment;

        protected T self() {
            return (T) this;
        }

        public T name(String name) {
            this.name = name;
            return self();
        }

        public T systemPrompt(String systemPrompt) {
            this.systemPrompt = systemPrompt;
            return self();
        }

        public T openApiSpec(String openApiSpec) {
            this.openApiSpec = openApiSpec;
            return self();
        }

        public T provider(String provider) {
            this.provider = provider;
            return self();
        }

        public T model(String model) {
            this.model = model;
            return self();
        }

        public T apiKey(String apiKey) {
            this.apiKey = apiKey;
            return self();
        }

        public T apiBaseUrl(String apiBaseUrl) {
            this.apiBaseUrl = apiBaseUrl;
            return self();
        }

        public T apiAuth(String apiAuth) {
            this.apiAuth = apiAuth;
            return self();
        }

        public T endpoints(String endpoints) {
            this.endpoints = endpoints;
            return self();
        }

        public T enableQuickReplies(Boolean enableQuickReplies) {
            this.enableQuickReplies = enableQuickReplies;
            return self();
        }

        public T enableSentimentAnalysis(Boolean enableSentimentAnalysis) {
            this.enableSentimentAnalysis = enableSentimentAnalysis;
            return self();
        }

        public T deploy(Boolean deploy) {
            this.deploy = deploy;
            return self();
        }

        public T environment(String environment) {
            this.environment = environment;
            return self();
        }

        public CreateApiAgentRequest build() {
            return new CreateApiAgentRequest(name, systemPrompt, openApiSpec, provider, model, apiKey,
                    apiBaseUrl, apiAuth, endpoints, enableQuickReplies, enableSentimentAnalysis, deploy, environment);
        }
    }
}
