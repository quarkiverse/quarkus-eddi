package io.quarkiverse.eddi.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Human-in-the-Loop configuration for an agent created through the setup APIs.
 * <p>
 * Matches EDDI v6's {@code AgentConfiguration.HitlConfig}. All fields are optional;
 * unset fields fall back to EDDI's defaults (e.g. {@code WAIT_INDEFINITELY}).
 *
 * <pre>{@code
 * HitlConfig.gateTools(List.of("*"), "Tool calls need a human OK")
 * }</pre>
 *
 * @param approvalTimeout ISO-8601 duration before {@code timeoutPolicy} applies (e.g. {@code PT30M})
 * @param timeoutPolicy what happens when the approval times out
 * @param pauseReason human-readable reason shown to approvers
 * @param toolApprovals gate for LLM tool calls
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record HitlConfig(
        String approvalTimeout,
        TimeoutPolicy timeoutPolicy,
        String pauseReason,
        ToolApprovals toolApprovals) {

    /**
     * Require approval before the listed tools run ({@code "*"} for all tools).
     */
    public static HitlConfig gateTools(List<String> requireApproval, String pauseReason) {
        return new HitlConfig(null, null, null,
                new ToolApprovals(requireApproval, null, null, null, null, null, null, pauseReason, null, null, null));
    }

    /**
     * Matches EDDI's {@code HitlTimeoutPolicy}.
     */
    public enum TimeoutPolicy {
        WAIT_INDEFINITELY,
        AUTO_APPROVE,
        AUTO_REJECT,
        ABORT
    }

    /**
     * Tool-call approval gate. Matches EDDI's {@code ToolApprovalsConfig}.
     *
     * @param requireApproval tool name patterns that need approval ({@code "*"} = all)
     * @param exempt tool name patterns exempt from approval
     * @param maxPausesPerTurn maximum approval pauses in a single turn
     * @param maxAutoApprovalsPerTurn maximum auto-approvals in a single turn
     * @param onNoProgress behaviour when a turn makes no progress
     * @param approvalTimeout ISO-8601 duration override for tool pauses
     * @param timeoutPolicy timeout policy override for tool pauses
     * @param pauseReason reason shown to approvers
     * @param pendingMessage message shown to the user while waiting
     * @param inGroupTurns behaviour of the gate inside group discussions
     * @param rules per-tool overrides
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ToolApprovals(
            List<String> requireApproval,
            List<String> exempt,
            Integer maxPausesPerTurn,
            Integer maxAutoApprovalsPerTurn,
            String onNoProgress,
            String approvalTimeout,
            TimeoutPolicy timeoutPolicy,
            String pauseReason,
            String pendingMessage,
            String inGroupTurns,
            List<ApprovalRule> rules) {
    }

    /**
     * Per-tool override inside {@link ToolApprovals}.
     *
     * @param match tool name pattern this rule applies to
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ApprovalRule(
            String match,
            TimeoutPolicy timeoutPolicy,
            String approvalTimeout,
            String pauseReason,
            String pendingMessage) {
    }
}
