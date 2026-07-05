package io.quarkiverse.eddi.model;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A human decision submitted to resume a paused (HITL) conversation or group
 * discussion.
 * <p>
 * Matches EDDI v6's {@code ai.labs.eddi.engine.lifecycle.model.HitlDecision}.
 * The server sets {@code decidedBy} itself from the caller's security identity,
 * so it is intentionally not part of this client-side request body. The server
 * caps {@code note} at 4096 characters.
 * <p>
 * For a rule-based (RULE) pause a top-level {@link #verdict} is sufficient. For a
 * tool-call (TOOL_CALL) pause, {@link #toolDecisions} may carry a per-call
 * verdict keyed by the tool call id; calls not listed inherit the top-level
 * verdict.
 *
 * @param verdict the overall approval/rejection verdict
 * @param note optional free-text reviewer note (server max 4096 chars)
 * @param toolDecisions optional per-tool-call verdicts, keyed by tool call id
 *        (TOOL_CALL pauses only)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record HitlDecision(
        HitlVerdict verdict,
        String note,
        Map<String, ToolCallDecision> toolDecisions) {

    /** Approve with no note. */
    public static HitlDecision approve() {
        return new HitlDecision(HitlVerdict.APPROVED, null, null);
    }

    /** Approve with an optional reviewer note. */
    public static HitlDecision approve(String note) {
        return new HitlDecision(HitlVerdict.APPROVED, note, null);
    }

    /** Reject with an optional reviewer note. */
    public static HitlDecision reject(String note) {
        return new HitlDecision(HitlVerdict.REJECTED, note, null);
    }

    /**
     * Approve/reject individual gated tool calls within a TOOL_CALL pause.
     *
     * @param verdict the fallback verdict for calls not listed in
     *        {@code toolDecisions}
     * @param note optional reviewer note
     * @param toolDecisions per-call verdicts keyed by tool call id
     */
    public static HitlDecision ofToolCalls(HitlVerdict verdict, String note,
            Map<String, ToolCallDecision> toolDecisions) {
        return new HitlDecision(verdict, note, toolDecisions);
    }
}
