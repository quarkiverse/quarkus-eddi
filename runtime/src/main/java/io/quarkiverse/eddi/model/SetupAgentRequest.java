package io.quarkiverse.eddi.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request object for setting up a standard EDDI agent.
 * <p>
 * Matches EDDI v6's {@code SetupAgentRequest} record. Maps to
 * {@code POST /administration/agents/setup}.
 * <p>
 * Example:
 *
 * <pre>{@code
 * var request = SetupAgentRequest.builder()
 *         .name("Support Bot")
 *         .systemPrompt("You are a helpful support agent.")
 *         .provider("openai").model("gpt-4o")
 *         .apiKey(config.openaiKey())
 *         .deploy(true)
 *         .build();
 * }</pre>
 */
public record SetupAgentRequest(
        @JsonProperty(required = true) String name,
        @JsonProperty(required = true) String systemPrompt,
        String provider,
        String model,
        String apiKey,
        String baseUrl,
        String introMessage,
        Boolean enableBuiltInTools,
        String builtInToolsWhitelist,
        Boolean enableQuickReplies,
        Boolean enableSentimentAnalysis,
        String mcpServerUrls,
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
        private String provider;
        private String model;
        private String apiKey;
        private String baseUrl;
        private String introMessage;
        private Boolean enableBuiltInTools;
        private String builtInToolsWhitelist;
        private Boolean enableQuickReplies;
        private Boolean enableSentimentAnalysis;
        private String mcpServerUrls;
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

        public T baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return self();
        }

        public T introMessage(String introMessage) {
            this.introMessage = introMessage;
            return self();
        }

        public T enableBuiltInTools(Boolean enableBuiltInTools) {
            this.enableBuiltInTools = enableBuiltInTools;
            return self();
        }

        public T builtInToolsWhitelist(String builtInToolsWhitelist) {
            this.builtInToolsWhitelist = builtInToolsWhitelist;
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

        public T mcpServerUrls(String mcpServerUrls) {
            this.mcpServerUrls = mcpServerUrls;
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

        public SetupAgentRequest build() {
            return new SetupAgentRequest(name, systemPrompt, provider, model, apiKey, baseUrl, introMessage,
                    enableBuiltInTools, builtInToolsWhitelist, enableQuickReplies, enableSentimentAnalysis,
                    mcpServerUrls, deploy, environment);
        }
    }
}
