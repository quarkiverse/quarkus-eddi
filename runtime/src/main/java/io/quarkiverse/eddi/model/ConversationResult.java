package io.quarkiverse.eddi.model;

import java.util.List;
import java.util.Map;

/**
 * High-level result from an EDDI conversation step.
 * Extracts the most useful fields from the raw conversation memory snapshot.
 */
public record ConversationResult(
        String conversationId,
        String agentResponse,
        List<String> agentResponseParts,
        List<String> quickReplies,
        List<String> actions,
        String conversationState,
        Map<String, Object> rawSnapshot) {

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
        return "ENDED".equals(conversationState);
    }
}
