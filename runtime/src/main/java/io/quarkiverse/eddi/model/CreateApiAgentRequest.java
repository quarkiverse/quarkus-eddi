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

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
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

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder systemPrompt(String systemPrompt) {
            this.systemPrompt = systemPrompt;
            return this;
        }

        public Builder openApiSpec(String openApiSpec) {
            this.openApiSpec = openApiSpec;
            return this;
        }

        public Builder provider(String provider) {
            this.provider = provider;
            return this;
        }

        public Builder model(String model) {
            this.model = model;
            return this;
        }

        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public Builder apiBaseUrl(String apiBaseUrl) {
            this.apiBaseUrl = apiBaseUrl;
            return this;
        }

        public Builder apiAuth(String apiAuth) {
            this.apiAuth = apiAuth;
            return this;
        }

        public Builder endpoints(String endpoints) {
            this.endpoints = endpoints;
            return this;
        }

        public Builder enableQuickReplies(Boolean enableQuickReplies) {
            this.enableQuickReplies = enableQuickReplies;
            return this;
        }

        public Builder enableSentimentAnalysis(Boolean enableSentimentAnalysis) {
            this.enableSentimentAnalysis = enableSentimentAnalysis;
            return this;
        }

        public Builder deploy(Boolean deploy) {
            this.deploy = deploy;
            return this;
        }

        public Builder environment(String environment) {
            this.environment = environment;
            return this;
        }

        public CreateApiAgentRequest build() {
            return new CreateApiAgentRequest(name, systemPrompt, openApiSpec, provider, model, apiKey,
                    apiBaseUrl, apiAuth, endpoints, enableQuickReplies, enableSentimentAnalysis, deploy, environment);
        }
    }
}
