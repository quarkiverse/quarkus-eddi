package io.quarkiverse.eddi.model;

/**
 * A human's verdict on a paused (HITL) conversation or gated tool call.
 * <p>
 * Matches EDDI v6's {@code HitlDecision.HitlVerdict}. Serialized as its
 * uppercase name ({@code "APPROVED"} / {@code "REJECTED"}); the server parses
 * verdicts case-insensitively.
 */
public enum HitlVerdict {
    APPROVED,
    REJECTED
}
