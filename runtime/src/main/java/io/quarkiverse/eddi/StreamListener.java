package io.quarkiverse.eddi;

import io.quarkiverse.eddi.model.ConversationResult;

/**
 * Callback interface for consuming EDDI SSE streaming responses.
 * <p>
 * Implement this to receive real-time tokens, lifecycle events,
 * and the final conversation result.
 * <p>
 * Example:
 * <pre>{@code
 * conv.sayStreaming("Tell me a story", new StreamListener() {
 *     public void onToken(String text) { System.out.print(text); }
 *     public void onDone(ConversationResult result) { /* complete * / }
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
     * Called when the agent enters a thinking/reasoning phase.
     */
    default void onThinking() {
    }

    /**
     * Called when a workflow task starts.
     */
    default void onTaskStart(String taskName) {
    }

    /**
     * Called when a workflow task completes.
     */
    default void onTaskComplete(String taskName) {
    }

    /**
     * Called when the full response is ready.
     */
    default void onDone(ConversationResult result) {
    }

    /**
     * Called if an error occurs during streaming.
     */
    default void onError(Throwable error) {
    }
}
