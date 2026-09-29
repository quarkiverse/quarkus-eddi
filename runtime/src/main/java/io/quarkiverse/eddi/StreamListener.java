package io.quarkiverse.eddi;

import io.quarkiverse.eddi.model.ConversationResult;
import io.quarkiverse.eddi.model.StreamToken;

/**
 * Callback interface for consuming EDDI SSE streaming responses.
 * <p>
 * Implement this to receive real-time tokens, lifecycle events,
 * and the final conversation result.
 * <p>
 * Example:
 *
 * <pre>{@code
 * conv.sayStreaming("Tell me a story", new StreamListener() {
 *     public void onToken(String text) {
 *         System.out.print(text);
 *     }
 *
 *     public void onComplete(ConversationResult result) {
 *         System.out.println("\nDone!");
 *     }
 * });
 * }</pre>
 */
public interface StreamListener {

    /**
     * Called for each incremental text token from the LLM.
     */
    default void onToken(String text) {
    }

    /**
     * Called when a workflow task starts.
     *
     * @param taskId the task identifier
     * @param taskType the task type (e.g., "ai.labs.parser", "ai.labs.llm")
     * @param index the task index in the workflow
     */
    default void onTaskStart(String taskId, String taskType, int index) {
    }

    /**
     * Called when a workflow task completes.
     *
     * @param taskId the task identifier
     * @param taskType the task type
     * @param durationMs execution time in milliseconds
     */
    default void onTaskComplete(String taskId, String taskType, long durationMs) {
    }

    /**
     * Called when the LLM invokes a tool.
     *
     * @param toolName the tool name
     */
    default void onToolCall(String toolName) {
    }

    /**
     * Called when a workflow task fails. The stream continues; a terminal failure
     * is reported via {@link #onError(Throwable)}.
     *
     * @param taskId the task identifier
     * @param taskType the task type
     * @param durationMs execution time in milliseconds
     * @param errorType the error classification
     * @param error a short error summary
     */
    default void onTaskFailed(String taskId, String taskType, long durationMs, String errorType, String error) {
    }

    /**
     * Called for any other stream event without a dedicated callback (e.g.
     * {@code cascade_step_start}, {@code cascade_escalation}, or events added by
     * newer EDDI versions).
     */
    default void onEvent(StreamToken event) {
    }

    /**
     * Called when the full response is ready. For a turn that paused
     * ({@code AWAITING_HUMAN}), the result also carries the pause type and pending
     * tool names, read from the conversation since the SSE {@code done} payload
     * omits them.
     */
    default void onComplete(ConversationResult result) {
    }

    /**
     * Called if an error occurs during streaming. Errors that EDDI reports inside the
     * stream ({@code error} events) arrive as {@link EddiStreamException} with a
     * machine-readable {@code code()}. Rejections before the stream opens (e.g. HTTP 403,
     * 404 or 413) arrive as {@link jakarta.ws.rs.WebApplicationException}.
     */
    default void onError(Throwable error) {
    }
}
