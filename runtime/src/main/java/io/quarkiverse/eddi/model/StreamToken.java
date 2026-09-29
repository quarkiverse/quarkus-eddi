package io.quarkiverse.eddi.model;

/**
 * A single token/event from an EDDI SSE streaming response.
 * <p>
 * Matches v6's streaming event types from {@code RestAgentEngineStreaming}:
 * <ul>
 * <li>{@code task_start} — lifecycle task began (data: JSON with taskId, taskType, index)</li>
 * <li>{@code task_complete} — lifecycle task finished (data: JSON with taskId, taskType, durationMs,
 * optional actions/toolTrace/confidence)</li>
 * <li>{@code task_failed} — lifecycle task failed (data: JSON with taskId, taskType, durationMs, errorType, error)</li>
 * <li>{@code tool_call} — the LLM invoked a tool (data: JSON with tool)</li>
 * <li>{@code cascade_step_start} / {@code cascade_escalation} — model cascade progress (data: JSON)</li>
 * <li>{@code token} — LLM response token in real-time (data: raw text)</li>
 * <li>{@code done} — final snapshot (data: JSON with conversationState, optional hitlPausedAt and
 * conversationOutputs; unlike the REST snapshot it has no conversationId or HITL pause details)</li>
 * <li>{@code error} — error during processing (data: JSON with message and, for known conditions, code)</li>
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
     * Whether this is a lifecycle task failure.
     */
    public boolean isTaskFailed() {
        return "task_failed".equals(type);
    }

    /**
     * Whether this is a tool invocation by the LLM.
     */
    public boolean isToolCall() {
        return "tool_call".equals(type);
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
