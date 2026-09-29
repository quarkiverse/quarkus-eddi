package io.quarkiverse.eddi.model;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Summary of a conversation currently awaiting human approval (a HITL pause),
 * as returned by the pending-approvals inboxes. Carries no transcript — only the
 * coordinates an approver needs to triage.
 * <p>
 * Matches EDDI v6's {@code ai.labs.eddi.engine.model.PendingApprovalSummary}.
 *
 * @param conversationId the paused conversation id
 * @param agentId the agent that paused
 * @param groupId set only for group-surface pauses — the group configuration id
 * @param userId owner of the conversation
 * @param pausedAt when the conversation paused
 * @param pauseReason human-readable reason for the pause
 * @param timeoutPolicy the configured timeout policy name
 * @param approvalTimeout ISO-8601 duration of the configured approval timeout
 *        (may be null)
 * @param pauseType {@code null}/{@code "RULE"} = behavior-rule pause,
 *        {@code "TOOL_CALL"} = gated tool pause
 * @param toolNames names only (no arguments) of the gated tool calls, for
 *        TOOL_CALL pauses
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PendingApprovalSummary(
        String conversationId,
        String agentId,
        String groupId,
        String userId,
        Instant pausedAt,
        String pauseReason,
        String timeoutPolicy,
        String approvalTimeout,
        String pauseType,
        List<String> toolNames) {

    /** Whether this pause is a gated tool-call pause (vs a behavior-rule pause). */
    public boolean isToolCallPause() {
        return "TOOL_CALL".equals(pauseType);
    }
}
