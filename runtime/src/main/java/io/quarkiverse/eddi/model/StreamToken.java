package io.quarkiverse.eddi.model;

/**
 * A single token from an EDDI SSE streaming response.
 *
 * @param type  Event type: "token", "thinking", "task_start", "task_complete", "done", "error"
 * @param data  The token text or event payload
 */
public record StreamToken(String type, String data) {

    /**
     * Whether this is an incremental text token.
     */
    public boolean isToken() {
        return "token".equals(type);
    }

    /**
     * Whether the stream is complete.
     */
    public boolean isDone() {
        return "done".equals(type);
    }

    /**
     * Whether the agent is in a thinking/reasoning phase.
     */
    public boolean isThinking() {
        return "thinking".equals(type);
    }

    /**
     * Whether this is an error event.
     */
    public boolean isError() {
        return "error".equals(type);
    }

    /**
     * The text content (alias for data).
     */
    public String text() {
        return data != null ? data : "";
    }
}
