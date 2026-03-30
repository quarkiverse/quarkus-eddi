package io.quarkiverse.eddi;

import io.quarkiverse.eddi.model.ConversationResult;

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
     * Called when the full response is ready.
     */
    default void onComplete(ConversationResult result) {
    }

    /**
     * Called if an error occurs during streaming.
     */
    default void onError(Throwable error) {
    }
}
