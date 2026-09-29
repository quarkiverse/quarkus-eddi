package io.quarkiverse.eddi;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import jakarta.ws.rs.core.Response;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

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

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String conversationId;
    private final String agentId;
    private final EddiAgentRestClient agentClient;
    private final EddiStreamingRestClient streamingClient;
    private final Duration timeout;

    /**
     * Package-private constructor — conversations should be created via
     * {@link EddiClient.AgentBuilder#startConversation()}.
     */
    Conversation(String conversationId, String agentId,
            EddiAgentRestClient agentClient, EddiStreamingRestClient streamingClient) {
        this(conversationId, agentId, agentClient, streamingClient, EddiDefaults.DEFAULT_TIMEOUT);
    }

    Conversation(String conversationId, String agentId, EddiAgentRestClient agentClient,
            EddiStreamingRestClient streamingClient, Duration timeout) {
        this.timeout = timeout;
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
     * Send a text message (blocking, up to quarkus.eddi.timeout).
     */
    public ConversationResult say(String message) {
        return sayAsync(message).await().atMost(timeout);
    }

    /**
     * Send a message with context (blocking, up to quarkus.eddi.timeout).
     */
    public ConversationResult sayWithContext(String message, Map<String, Context> context) {
        return sayWithContextAsync(message, context).await().atMost(timeout);
    }

    // ─── Streaming ────────────────────────────────

    /**
     * Send a message and receive a streaming response.
     * <p>
     * Returns a {@link Multi} of {@link StreamToken} events. Token types include:
     * {@code task_start}, {@code task_complete}, {@code task_failed}, {@code tool_call},
     * {@code token}, {@code done}, {@code error} (see {@link StreamToken}).
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
                // Unnamed events default to "token"
                .filter(EddiClient::isEvent).map(EddiClient::toStreamToken);
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
                .subscribe().with(token -> dispatch(token, listener), listener::onError);
    }

    void dispatch(StreamToken token, StreamListener listener) {
        if (token.isToken()) {
            listener.onToken(token.text());
        } else if (token.isTaskStart()) {
            Map<String, Object> event = parseJson(token.text());
            listener.onTaskStart(str(event, "taskId"), str(event, "taskType"), (int) num(event, "index"));
        } else if (token.isTaskComplete()) {
            Map<String, Object> event = parseJson(token.text());
            listener.onTaskComplete(str(event, "taskId"), str(event, "taskType"), num(event, "durationMs"));
        } else if (token.isTaskFailed()) {
            Map<String, Object> event = parseJson(token.text());
            listener.onTaskFailed(str(event, "taskId"), str(event, "taskType"), num(event, "durationMs"),
                    str(event, "errorType"), str(event, "error"));
        } else if (token.isToolCall()) {
            listener.onToolCall(str(parseJson(token.text()), "tool"));
        } else if (token.isDone()) {
            ConversationResult result = parseDoneEvent(token.text());
            if (result.isAwaitingHuman()) {
                // The done payload has no pause type / pending tools; read them.
                completePausedResult(result).subscribe().with(listener::onComplete, listener::onError);
            } else {
                listener.onComplete(result);
            }
        } else if (token.isError()) {
            listener.onError(parseErrorEvent(token.text()));
        } else {
            listener.onEvent(token);
        }
    }

    /**
     * Re-read a paused conversation to fill in the HITL details the streaming
     * {@code done} event omits, falling back to the streamed result on failure.
     */
    Uni<ConversationResult> completePausedResult(ConversationResult streamed) {
        return agentClient.readConversation(conversationId, false, true, null)
                .map(snapshot -> {
                    ConversationResult full = toConversationResult(snapshot);
                    return new ConversationResult(conversationId, streamed.agentResponse(),
                            streamed.agentResponseParts(), streamed.quickReplies(), streamed.actions(),
                            streamed.conversationState(), full.hitlPauseType(),
                            streamed.hitlPausedAt() != null ? streamed.hitlPausedAt() : full.hitlPausedAt(),
                            full.hitlPendingToolNames(), full.undoAvailable(), full.redoAvailable());
                })
                .onFailure().recoverWithItem(streamed);
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
     * Rerun the last conversation step (blocking, up to quarkus.eddi.timeout).
     */
    public ConversationResult rerun() {
        return rerunAsync().await().atMost(timeout);
    }

    // ─── State ────────────────────────────────────

    /**
     * Get the current conversation lifecycle state (reactive).
     */
    public Uni<ConversationState> stateAsync() {
        return agentClient.getConversationState(conversationId);
    }

    /**
     * Get the current conversation lifecycle state (blocking, up to quarkus.eddi.timeout).
     */
    public ConversationState state() {
        return stateAsync().await().atMost(timeout);
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
     * Undo (blocking, up to quarkus.eddi.timeout).
     */
    public void undo() {
        undoAsync().await().atMost(timeout);
    }

    /**
     * Redo (blocking, up to quarkus.eddi.timeout).
     */
    public void redo() {
        redoAsync().await().atMost(timeout);
    }

    // ─── End ──────────────────────────────────────

    /**
     * End this conversation (reactive).
     */
    public Uni<Response> endAsync() {
        return agentClient.endConversation(conversationId);
    }

    /**
     * End this conversation (blocking, up to quarkus.eddi.timeout).
     */
    public void end() {
        endAsync().await().atMost(timeout);
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

    // ─── Cancel ───────────────────────────────────

    /**
     * Cancel this conversation, stopping any in-progress processing and clearing a
     * HITL pause (reactive).
     */
    public Uni<Response> cancelAsync() {
        return agentClient.cancelConversation(conversationId);
    }

    /**
     * Cancel this conversation (blocking, up to quarkus.eddi.timeout).
     */
    public void cancel() {
        cancelAsync().await().atMost(timeout).close();
    }

    // ─── Admin: state reset ───────────────────────

    /**
     * Reset a stuck conversation ({@code ERROR} or {@code EXECUTION_INTERRUPTED}) back
     * to {@code READY} (reactive). Requires the {@code eddi-admin} role.
     */
    public Uni<Response> resetStateAsync() {
        return agentClient.resetState(conversationId, ConversationState.READY.name());
    }

    /**
     * Reset a stuck conversation back to {@code READY} (blocking, up to quarkus.eddi.timeout).
     * Requires the {@code eddi-admin} role.
     */
    public void resetState() {
        resetStateAsync().await().atMost(timeout).close();
    }

    // ─── HITL (Human-in-the-Loop) ─────────────────

    /**
     * Whether this conversation is currently paused awaiting a human approval
     * decision (reactive).
     */
    public Uni<Boolean> isAwaitingHumanAsync() {
        return stateAsync().map(state -> ConversationState.AWAITING_HUMAN == state);
    }

    /**
     * Whether this conversation is currently paused awaiting a human approval
     * decision (blocking, up to quarkus.eddi.timeout).
     */
    public boolean isAwaitingHuman() {
        return isAwaitingHumanAsync().await().atMost(timeout);
    }

    /**
     * Submit a human decision to resume this conversation from an
     * {@code AWAITING_HUMAN} pause (reactive). The returned {@link Response} fails
     * with 409 if the conversation is not awaiting approval.
     */
    public Uni<Response> resumeAsync(HitlDecision decision) {
        return agentClient.resumeConversation(conversationId, decision);
    }

    /**
     * Submit a human decision to resume this conversation (blocking, up to quarkus.eddi.timeout).
     */
    public Response resume(HitlDecision decision) {
        return resumeAsync(decision).await().atMost(timeout);
    }

    /**
     * Approve and resume this paused conversation with an optional reviewer note
     * (reactive).
     */
    public Uni<Response> approveAsync(String note) {
        return resumeAsync(HitlDecision.approve(note));
    }

    /**
     * Approve and resume this paused conversation (blocking, up to quarkus.eddi.timeout).
     */
    public Response approve(String note) {
        return approveAsync(note).await().atMost(timeout);
    }

    /**
     * Reject and resume this paused conversation with an optional reviewer note
     * (reactive).
     */
    public Uni<Response> rejectAsync(String note) {
        return resumeAsync(HitlDecision.reject(note));
    }

    /**
     * Reject and resume this paused conversation (blocking, up to quarkus.eddi.timeout).
     */
    public Response reject(String note) {
        return rejectAsync(note).await().atMost(timeout);
    }

    /**
     * Read the HITL approval status of this conversation (reactive).
     *
     * @param detail {@code "summary"} (pause coordinates only) or {@code "full"}
     *        (complete memory snapshot)
     */
    public Uni<Response> approvalStatusAsync(String detail) {
        return agentClient.getApprovalStatus(conversationId, detail);
    }

    /**
     * Read the HITL approval status summary of this conversation (blocking, up to quarkus.eddi.timeout
     * timeout).
     */
    public Response approvalStatus() {
        return approvalStatusAsync("summary").await().atMost(timeout);
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

        var outputs = (List<Object>) snapshot.get("conversationOutputs");
        if (outputs != null && !outputs.isEmpty()) {
            Object latest = outputs.get(outputs.size() - 1);
            if (latest instanceof Map<?, ?> latestStep) {
                // The server serializes each output entry as a TextOutputItem object
                // ({"type":"text","text":"..."}); quick replies as QuickReply objects
                // ({"value":"...","expressions":"...","isDefault":false}). Extract the
                // text/value fields (with a String fallback for legacy shapes) rather
                // than casting the list elements to String, which would throw.
                responseParts.addAll(extractStrings(latestStep.get("output"), "text"));
                quickReplies.addAll(extractStrings(latestStep.get("quickReplies"), "value"));
                actions.addAll(extractStrings(latestStep.get("actions"), "value", "name", "key"));
            }
        }

        String agentResponse = responseParts.isEmpty() ? "" : String.join("\n", responseParts);

        return new ConversationResult(
                conversationId,
                agentResponse,
                Collections.unmodifiableList(responseParts),
                Collections.unmodifiableList(quickReplies),
                Collections.unmodifiableList(actions),
                state,
                (String) snapshot.get("hitlPauseType"),
                parseInstant(snapshot.get("hitlPausedAt")),
                extractPendingToolNames(snapshot.get("hitlPendingToolCalls")),
                Boolean.TRUE.equals(snapshot.get("undoAvailable")),
                Boolean.TRUE.equals(snapshot.get("redoAvailable")));
    }

    /**
     * Extract a list of strings from a snapshot value that may be a list of plain
     * strings (legacy) or a list of objects. For object elements, the first present
     * key in {@code keys} is used; elements matching none are skipped. Never throws
     * on unexpected element types.
     */
    private static List<String> extractStrings(Object raw, String... keys) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<String> out = new ArrayList<>(list.size());
        for (Object element : list) {
            if (element == null) {
                continue;
            }
            if (element instanceof String s) {
                out.add(s);
            } else if (element instanceof Map<?, ?> map) {
                for (String key : keys) {
                    Object value = map.get(key);
                    if (value != null) {
                        out.add(String.valueOf(value));
                        break;
                    }
                }
            } else {
                out.add(String.valueOf(element));
            }
        }
        return out;
    }

    /**
     * Extract the gated tool-call names (no arguments) from a serialized
     * {@code PendingToolCallBatch} ({@code {"calls":[{"toolName":"..."}]}}).
     */
    private static List<String> extractPendingToolNames(Object raw) {
        if (!(raw instanceof Map<?, ?> batch)) {
            return List.of();
        }
        return extractStrings(batch.get("calls"), "toolName");
    }

    /**
     * Parse an instant out of the raw snapshot map, returning {@code null} for
     * missing or unparseable values.
     * <p>
     * EDDI 6.4's REST layer serializes an {@code Instant} as an ISO-8601 string
     * (its {@code SerializationCustomizer} overrides the global
     * {@code write-dates-as-timestamps=true} for {@code Instant}). A number is still
     * accepted and read as fractional epoch-<em>seconds</em> (e.g. {@code 1719964800.123}).
     */
    private static Instant parseInstant(Object raw) {
        if (raw instanceof Instant instant) {
            return instant;
        }
        if (raw instanceof Number number) {
            double epochSeconds = number.doubleValue();
            long seconds = (long) Math.floor(epochSeconds);
            long nanos = Math.round((epochSeconds - seconds) * 1_000_000_000L);
            return Instant.ofEpochSecond(seconds, nanos);
        }
        if (raw instanceof String s && !s.isBlank()) {
            try {
                return Instant.parse(s);
            } catch (RuntimeException ignored) {
                return null;
            }
        }
        return null;
    }

    static ConversationState parseState(String stateStr) {
        try {
            return ConversationState.valueOf(stateStr);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    static EddiStreamException parseErrorEvent(String data) {
        Map<String, Object> event = parseJson(data);
        if (event.isEmpty()) {
            return new EddiStreamException(null, data);
        }
        return new EddiStreamException(str(event, "code"), str(event, "message"));
    }

    private static Map<String, Object> parseJson(String data) {
        if (data == null || data.isBlank()) {
            return Map.of();
        }
        try {
            return MAPPER.readValue(data, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception notJson) {
            return Map.of();
        }
    }

    private static String str(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? String.valueOf(value) : null;
    }

    private static long num(Map<String, Object> map, String key) {
        return map.get(key) instanceof Number n ? n.longValue() : 0L;
    }

    /**
     * Parse the "done" SSE event into a {@link ConversationResult}.
     * <p>
     * The done event data is a reduced conversation snapshot as JSON. We parse it
     * through {@link #toConversationResult(Map)} so the listener receives the same
     * structured result (text, quick replies, actions, HITL metadata) as the
     * non-streaming path. If the data is not JSON, it is surfaced as raw response
     * text.
     */
    private ConversationResult parseDoneEvent(String data) {
        if (data == null || data.isBlank()) {
            return new ConversationResult(conversationId, "", List.of(), List.of(), List.of(), null);
        }
        try {
            Map<String, Object> snapshot = MAPPER.readValue(data, new TypeReference<Map<String, Object>>() {
            });
            snapshot.putIfAbsent("conversationId", conversationId);
            return toConversationResult(snapshot);
        } catch (Exception notJson) {
            // Fallback: the server sent plain text rather than a snapshot.
            return new ConversationResult(conversationId, data, List.of(data), List.of(), List.of(),
                    ConversationState.READY);
        }
    }
}
