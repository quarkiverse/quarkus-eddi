package io.quarkiverse.eddi.model;

import java.time.Instant;
import java.util.List;

/**
 * High-level result from an EDDI conversation step.
 * <p>
 * Extracts the most useful fields from the raw
 * {@code SimpleConversationMemorySnapshot} returned by the v6 API, including the
 * HITL (Human-in-the-Loop) pause metadata carried on the snapshot.
 *
 * @param conversationId the conversation ID
 * @param agentResponse the primary text response
 * @param agentResponseParts individual response parts (multi-output support)
 * @param quickReplies suggested quick reply buttons
 * @param actions actions triggered by the agent
 * @param conversationState the conversation lifecycle state
 * @param hitlPauseType when paused, the pause type: {@code "TOOL_CALL"},
 *        {@code "RULE"}, or {@code null}
 * @param hitlPausedAt when the conversation paused awaiting a human (or
 *        {@code null} if not paused)
 * @param hitlPendingToolNames names only (no arguments) of the gated tool calls
 *        awaiting approval, for a TOOL_CALL pause
 * @param undoAvailable whether the last step can be undone
 * @param redoAvailable whether a previously undone step can be redone
 */
public record ConversationResult(
        String conversationId,
        String agentResponse,
        List<String> agentResponseParts,
        List<String> quickReplies,
        List<String> actions,
        ConversationState conversationState,
        String hitlPauseType,
        Instant hitlPausedAt,
        List<String> hitlPendingToolNames,
        boolean undoAvailable,
        boolean redoAvailable) {

    public ConversationResult {
        agentResponseParts = agentResponseParts == null ? List.of() : agentResponseParts;
        quickReplies = quickReplies == null ? List.of() : quickReplies;
        actions = actions == null ? List.of() : actions;
        hitlPendingToolNames = hitlPendingToolNames == null ? List.of() : hitlPendingToolNames;
    }

    /**
     * Backwards-compatible constructor for the pre-HITL shape — leaves all HITL
     * metadata empty.
     */
    public ConversationResult(String conversationId, String agentResponse,
            List<String> agentResponseParts, List<String> quickReplies,
            List<String> actions, ConversationState conversationState) {
        this(conversationId, agentResponse, agentResponseParts, quickReplies, actions,
                conversationState, null, null, List.of(), false, false);
    }

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

    /**
     * Whether the conversation is paused awaiting a human approval decision (HITL).
     * When {@code true}, resume it with {@code Conversation.resume(...)},
     * {@code Conversation.approve(...)}, or {@code Conversation.reject(...)}.
     */
    public boolean isAwaitingHuman() {
        return ConversationState.AWAITING_HUMAN == conversationState;
    }
}
