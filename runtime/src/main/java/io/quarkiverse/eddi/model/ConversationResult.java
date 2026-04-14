package io.quarkiverse.eddi.model;

import java.util.List;

/**
 * High-level result from an EDDI conversation step.
 * <p>
 * Extracts the most useful fields from the raw
 * {@code SimpleConversationMemorySnapshot} returned by the v6 API.
 *
 * @param conversationId the conversation ID
 * @param agentResponse the primary text response
 * @param agentResponseParts individual response parts (multi-output support)
 * @param quickReplies suggested quick reply buttons
 * @param actions actions triggered by the agent
 * @param conversationState the conversation lifecycle state
 */
public record ConversationResult(
        String conversationId,
        String agentResponse,
        List<String> agentResponseParts,
        List<String> quickReplies,
        List<String> actions,
        ConversationState conversationState) {

    /**
     * Convenience: returns the agent's text response, or empty string if none.
     */
    public String text() {
        return agentResponse != null ? agentResponse : "";
    }

    /**
     * Whether the conversation has ended.
     */
    public boolean isEnded() {
        return ConversationState.ENDED == conversationState;
    }
}
