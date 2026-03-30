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

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
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

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder systemPrompt(String systemPrompt) {
            this.systemPrompt = systemPrompt;
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

        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public Builder introMessage(String introMessage) {
            this.introMessage = introMessage;
            return this;
        }

        public Builder enableBuiltInTools(Boolean enableBuiltInTools) {
            this.enableBuiltInTools = enableBuiltInTools;
            return this;
        }

        public Builder builtInToolsWhitelist(String builtInToolsWhitelist) {
            this.builtInToolsWhitelist = builtInToolsWhitelist;
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

        public Builder mcpServerUrls(String mcpServerUrls) {
            this.mcpServerUrls = mcpServerUrls;
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

        public SetupAgentRequest build() {
            return new SetupAgentRequest(name, systemPrompt, provider, model, apiKey, baseUrl, introMessage,
                    enableBuiltInTools, builtInToolsWhitelist, enableQuickReplies, enableSentimentAnalysis,
                    mcpServerUrls, deploy, environment);
        }
    }
}
