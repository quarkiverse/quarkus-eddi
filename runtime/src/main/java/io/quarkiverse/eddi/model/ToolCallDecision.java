package io.quarkiverse.eddi.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Per-tool-call verdict inside a {@link HitlDecision} resume body (TOOL_CALL
 * pauses only).
 * <p>
 * Matches EDDI v6's {@code ai.labs.eddi.engine.lifecycle.model.ToolCallDecision}.
 * Lets a reviewer approve/reject individual gated tool calls within the same
 * pending batch, optionally amending the arguments of an approved call before it
 * executes. The server caps {@code note} at 1024 characters.
 *
 * @param verdict the verdict for this specific tool call
 * @param note optional free-text reviewer note (server max 1024 chars)
 * @param amendedArguments optional replacement arguments (raw JSON) for an
 *        approved call, applied before execution
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ToolCallDecision(
        HitlVerdict verdict,
        String note,
        String amendedArguments) {

    /** Approve a single gated tool call as-is. */
    public static ToolCallDecision approve() {
        return new ToolCallDecision(HitlVerdict.APPROVED, null, null);
    }

    /** Approve a single gated tool call, replacing its arguments before execution. */
    public static ToolCallDecision approveWithArguments(String amendedArguments) {
        return new ToolCallDecision(HitlVerdict.APPROVED, null, amendedArguments);
    }

    /** Reject a single gated tool call with an optional note. */
    public static ToolCallDecision reject(String note) {
        return new ToolCallDecision(HitlVerdict.REJECTED, note, null);
    }
}
