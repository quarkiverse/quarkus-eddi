package io.quarkiverse.eddi;

import static io.quarkiverse.eddi.EddiDefaults.DEFAULT_TIMEOUT;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import jakarta.ws.rs.core.Response;

import io.quarkiverse.eddi.client.EddiAgentRestClient;
import io.quarkiverse.eddi.client.EddiStreamingRestClient;
import io.quarkiverse.eddi.model.*;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.subscription.Cancellable;

/**
 * Represents an active conversation with an EDDI agent.
 * <p>
 * Provides both reactive ({@code Uni<T>}) and blocking convenience methods.
 * <p>
 * Example:
 *
 * <pre>{@code
 * // Blocking
 * ConversationResult result = conv.say("Hello!");
 * System.out.println(result.text());
 *
 * // Reactive
 * conv.sayAsync("Hello!")
 *         .subscribe().with(r -> System.out.println(r.text()));
 *
 * // Streaming
 * conv.sayStreaming("Hello!")
 *         .subscribe().with(token -> {
 *             if (token.isToken())
 *                 System.out.print(token.text());
 *             if (token.isDone())
 *                 System.out.println("\n[Complete]");
 *         });
 * }</pre>
 */
public class Conversation {

    private final String conversationId;
    private final String agentId;
    private final EddiAgentRestClient agentClient;
    private final EddiStreamingRestClient streamingClient;

    /**
     * Package-private constructor — conversations should be created via
     * {@link EddiClient.AgentBuilder#startConversation()}.
     */
    Conversation(String conversationId, String agentId,
            EddiAgentRestClient agentClient, EddiStreamingRestClient streamingClient) {
        this.conversationId = conversationId;
        this.agentId = agentId;
        this.agentClient = agentClient;
        this.streamingClient = streamingClient;
    }

    // ─── Identity ─────────────────────────────────

    public String id() {
        return conversationId;
    }

    public String agentId() {
        return agentId;
    }

    // ─── Say (reactive) ───────────────────────────

    /**
     * Send a text message (reactive).
     */
    public Uni<ConversationResult> sayAsync(String message) {
        return agentClient.say(conversationId, false, true, null, message)
                .map(Conversation::toConversationResult);
    }

    /**
     * Send a message with context (reactive).
     */
    public Uni<ConversationResult> sayWithContextAsync(String message, Map<String, Context> context) {
        InputData inputData = InputData.of(message, context);
        return agentClient.sayWithinContext(conversationId, false, true, null, inputData)
                .map(Conversation::toConversationResult);
    }

    /**
     * Send structured input data (reactive).
     */
    public Uni<ConversationResult> sayWithContextAsync(InputData inputData) {
        return agentClient.sayWithinContext(conversationId, false, true, null, inputData)
                .map(Conversation::toConversationResult);
    }

    // ─── Say (blocking convenience) ───────────────

    /**
     * Send a text message (blocking, 30s timeout).
     */
    public ConversationResult say(String message) {
        return sayAsync(message).await().atMost(DEFAULT_TIMEOUT);
    }

    /**
     * Send a message with context (blocking, 30s timeout).
     */
    public ConversationResult sayWithContext(String message, Map<String, Context> context) {
        return sayWithContextAsync(message, context).await().atMost(DEFAULT_TIMEOUT);
    }

    // ─── Streaming ────────────────────────────────

    /**
     * Send a message and receive a streaming response.
     * <p>
     * Returns a {@link Multi} of {@link StreamToken} events. Token types include:
     * {@code task_start}, {@code task_complete}, {@code token}, {@code done}, {@code error}.
     * <p>
     * The SSE event name is preserved from the server, so {@code isToken()},
     * {@code isDone()}, {@code isError()} etc. work correctly.
     */
    public Multi<StreamToken> sayStreaming(String message) {
        return sayStreaming(InputData.of(message));
    }

    /**
     * Send structured input and receive a streaming response.
     */
    public Multi<StreamToken> sayStreaming(InputData inputData) {
        return streamingClient.sayStreaming(conversationId, false, true, null, inputData)
                .map(sseEvent -> {
                    String eventName = sseEvent.getName();
                    String data = sseEvent.readData();
                    // Default to "token" if the server sends an unnamed event
                    String type = (eventName != null && !eventName.isEmpty()) ? eventName : "token";
                    return new StreamToken(type, data);
                });
    }

    /**
     * Send a message with a streaming callback listener.
     * <p>
     * Returns a {@link Cancellable} that can be used to cancel the streaming
     * subscription.
     *
     * @param message the message to send
     * @param listener callback listener for streaming events
     * @return a cancellable subscription handle
     */
    public Cancellable sayStreaming(String message, StreamListener listener) {
        return sayStreaming(message)
                .subscribe().with(
                        token -> {
                            if (token.isToken()) {
                                listener.onToken(token.text());
                            } else if (token.isTaskStart()) {
                                listener.onTaskStart(token.text(), token.type(), 0);
                            } else if (token.isTaskComplete()) {
                                listener.onTaskComplete(token.text(), token.type(), 0);
                            } else if (token.isDone()) {
                                // Parse the done event data into a ConversationResult
                                listener.onComplete(parseDoneEvent(token.text()));
                            } else if (token.isError()) {
                                listener.onError(new RuntimeException(token.text()));
                            }
                        },
                        listener::onError);
    }

    // ─── Rerun ────────────────────────────────────

    /**
     * Rerun the last conversation step (reactive).
     */
    public Uni<ConversationResult> rerunAsync() {
        return agentClient.rerunLastConversationStep(conversationId, null, false, true, null)
                .map(Conversation::toConversationResult);
    }

    /**
     * Rerun the last conversation step (blocking, 30s timeout).
     */
    public ConversationResult rerun() {
        return rerunAsync().await().atMost(DEFAULT_TIMEOUT);
    }

    // ─── State ────────────────────────────────────

    /**
     * Get the current conversation lifecycle state (reactive).
     */
    public Uni<ConversationState> stateAsync() {
        return agentClient.getConversationState(conversationId);
    }

    /**
     * Get the current conversation lifecycle state (blocking, 30s timeout).
     */
    public ConversationState state() {
        return stateAsync().await().atMost(DEFAULT_TIMEOUT);
    }

    // ─── Read ─────────────────────────────────────

    /**
     * Read the full conversation memory snapshot (reactive).
     */
    public Uni<Map<String, Object>> readAsync() {
        return agentClient.readConversation(conversationId, false, false, null);
    }

    /**
     * Read the conversation log (reactive).
     */
    public Uni<Response> logAsync() {
        return agentClient.readConversationLog(conversationId, "json", -1);
    }

    // ─── Undo / Redo ──────────────────────────────

    /**
     * Check if undo is available (reactive).
     */
    public Uni<Boolean> isUndoAvailableAsync() {
        return agentClient.isUndoAvailable(conversationId);
    }

    /**
     * Undo the last conversation step (reactive).
     */
    public Uni<Response> undoAsync() {
        return agentClient.undo(conversationId);
    }

    /**
     * Check if redo is available (reactive).
     */
    public Uni<Boolean> isRedoAvailableAsync() {
        return agentClient.isRedoAvailable(conversationId);
    }

    /**
     * Redo the last undone step (reactive).
     */
    public Uni<Response> redoAsync() {
        return agentClient.redo(conversationId);
    }

    /**
     * Undo (blocking, 30s timeout).
     */
    public void undo() {
        undoAsync().await().atMost(DEFAULT_TIMEOUT);
    }

    /**
     * Redo (blocking, 30s timeout).
     */
    public void redo() {
        redoAsync().await().atMost(DEFAULT_TIMEOUT);
    }

    // ─── End ──────────────────────────────────────

    /**
     * End this conversation (reactive).
     */
    public Uni<Response> endAsync() {
        return agentClient.endConversation(conversationId);
    }

    /**
     * End this conversation (blocking, 30s timeout).
     */
    public void end() {
        endAsync().await().atMost(DEFAULT_TIMEOUT);
    }

    /**
     * End this conversation, swallowing any errors.
     * Used for cleanup in try/finally blocks.
     */
    void endQuietly() {
        try {
            end();
        } catch (Exception ignored) {
            // Best-effort cleanup
        }
    }

    // ─── Snapshot parsing ─────────────────────────

    @SuppressWarnings("unchecked")
    static ConversationResult toConversationResult(Map<String, Object> snapshot) {
        if (snapshot == null) {
            return new ConversationResult(null, "", List.of(), List.of(), List.of(), null);
        }

        String conversationId = (String) snapshot.get("conversationId");
        String stateStr = (String) snapshot.get("conversationState");
        ConversationState state = stateStr != null ? parseState(stateStr) : null;

        List<String> responseParts = new ArrayList<>();
        List<String> quickReplies = new ArrayList<>();
        List<String> actions = new ArrayList<>();

        var outputs = (List<Map<String, Object>>) snapshot.get("conversationOutputs");
        if (outputs != null && !outputs.isEmpty()) {
            var latestStep = outputs.get(outputs.size() - 1);

            var outputList = (List<String>) latestStep.get("output");
            if (outputList != null) {
                responseParts.addAll(outputList);
            }

            var qrList = (List<String>) latestStep.get("quickReplies");
            if (qrList != null) {
                quickReplies.addAll(qrList);
            }

            var actionList = (List<String>) latestStep.get("actions");
            if (actionList != null) {
                actions.addAll(actionList);
            }
        }

        String agentResponse = responseParts.isEmpty() ? "" : String.join("\n", responseParts);

        return new ConversationResult(
                conversationId,
                agentResponse,
                Collections.unmodifiableList(responseParts),
                Collections.unmodifiableList(quickReplies),
                Collections.unmodifiableList(actions),
                state);
    }

    static ConversationState parseState(String stateStr) {
        try {
            return ConversationState.valueOf(stateStr);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Parse the "done" SSE event into a simple ConversationResult.
     * <p>
     * The done event data is either JSON or plain text. We return it as
     * the agent response text — callers who need the full snapshot should use
     * {@link #readAsync()} after streaming completes.
     */
    private ConversationResult parseDoneEvent(String data) {
        if (data == null || data.isBlank()) {
            return new ConversationResult(conversationId, "", List.of(), List.of(), List.of(), null);
        }
        // Return the raw done event data as the response text
        return new ConversationResult(conversationId, data, List.of(data), List.of(), List.of(),
                ConversationState.READY);
    }
}
