package io.quarkiverse.eddi.model;

/**
 * Conversation lifecycle state.
 * <p>
 * Matches EDDI v6's {@code ConversationState} / {@code ConversationStatus}.
 */
public enum ConversationState {
    READY,
    IN_PROGRESS,
    ERROR,
    ENDED
}
