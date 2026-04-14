package io.quarkiverse.eddi.model;

/**
 * A single token/event from an EDDI SSE streaming response.
 * <p>
 * Matches v6's streaming event types from {@code RestAgentEngineStreaming}:
 * <ul>
 * <li>{@code task_start} — lifecycle task began (data: JSON with taskId, taskType, index)</li>
 * <li>{@code task_complete} — lifecycle task finished (data: JSON with taskId, taskType, durationMs)</li>
 * <li>{@code token} — LLM response token in real-time (data: raw text)</li>
 * <li>{@code progress} — workflow progress update</li>
 * <li>{@code done} — full conversation snapshot (data: JSON with conversationState, conversationOutputs)</li>
 * <li>{@code error} — error during processing (data: JSON with message)</li>
 * </ul>
 *
 * @param type SSE event name
 * @param data the event payload (raw text for tokens, JSON for lifecycle events)
 */
public record StreamToken(String type, String data) {

    /**
     * Whether this is an incremental text token from the LLM.
     */
    public boolean isToken() {
        return "token".equals(type);
    }

    /**
     * Whether the stream is complete (final snapshot available).
     */
    public boolean isDone() {
        return "done".equals(type);
    }

    /**
     * Whether this is a lifecycle task starting.
     */
    public boolean isTaskStart() {
        return "task_start".equals(type);
    }

    /**
     * Whether this is a lifecycle task completion.
     */
    public boolean isTaskComplete() {
        return "task_complete".equals(type);
    }

    /**
     * Whether this is a workflow progress event.
     */
    public boolean isProgress() {
        return "progress".equals(type);
    }

    /**
     * Whether this is an error event.
     */
    public boolean isError() {
        return "error".equals(type);
    }

    /**
     * The text content (alias for data). For token events, this is the raw LLM text.
     */
    public String text() {
        return data != null ? data : "";
    }
}
