package io.quarkiverse.eddi.model;

/**
 * Conversation lifecycle state.
 * <p>
 * Matches EDDI v6's {@code ai.labs.eddi.engine.memory.model.ConversationState}
 * exactly — every server constant must be present here, otherwise deserializing
 * the state of a conversation in that state fails. {@link #AWAITING_HUMAN} was
 * added in the v6 HITL (Human-in-the-Loop) framework and is set whenever a
 * conversation pauses for human approval.
 */
public enum ConversationState {
    READY,
    IN_PROGRESS,
    ENDED,
    EXECUTION_INTERRUPTED,
    ERROR,
    /** The conversation is paused awaiting a human approval decision (HITL). */
    AWAITING_HUMAN
}
