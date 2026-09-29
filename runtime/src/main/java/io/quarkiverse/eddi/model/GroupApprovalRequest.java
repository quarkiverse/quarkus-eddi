package io.quarkiverse.eddi.model;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Request body for approving/resuming a paused group discussion phase.
 * <p>
 * Matches EDDI v6's {@code ai.labs.eddi.engine.internal.GroupApprovalRequest}.
 *
 * @param decision the human decision (verdict + optional note + optional
 *        per-tool-call verdicts)
 * @param taskApprovals optional per-task verdicts, keyed by task id, each value
 *        being {@code "APPROVED"} or {@code "REJECTED"}
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GroupApprovalRequest(
        HitlDecision decision,
        Map<String, String> taskApprovals) {

    /** Wrap a {@link HitlDecision} with no per-task approvals. */
    public static GroupApprovalRequest of(HitlDecision decision) {
        return new GroupApprovalRequest(decision, null);
    }
}
