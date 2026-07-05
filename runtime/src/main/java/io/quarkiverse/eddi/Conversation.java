package io.quarkiverse.eddi;

import static io.quarkiverse.eddi.EddiDefaults.DEFAULT_TIMEOUT;

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

    // ─── Cancel ───────────────────────────────────

    /**
     * Cancel this conversation, stopping any in-progress processing and clearing a
     * HITL pause (reactive).
     */
    public Uni<Response> cancelAsync() {
        return agentClient.cancelConversation(conversationId);
    }

    /**
     * Cancel this conversation (blocking, 30s timeout).
     */
    public void cancel() {
        cancelAsync().await().atMost(DEFAULT_TIMEOUT);
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
     * decision (blocking, 30s timeout).
     */
    public boolean isAwaitingHuman() {
        return isAwaitingHumanAsync().await().atMost(DEFAULT_TIMEOUT);
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
     * Submit a human decision to resume this conversation (blocking, 30s timeout).
     */
    public Response resume(HitlDecision decision) {
        return resumeAsync(decision).await().atMost(DEFAULT_TIMEOUT);
    }

    /**
     * Approve and resume this paused conversation with an optional reviewer note
     * (reactive).
     */
    public Uni<Response> approveAsync(String note) {
        return resumeAsync(HitlDecision.approve(note));
    }

    /**
     * Approve and resume this paused conversation (blocking, 30s timeout).
     */
    public Response approve(String note) {
        return approveAsync(note).await().atMost(DEFAULT_TIMEOUT);
    }

    /**
     * Reject and resume this paused conversation with an optional reviewer note
     * (reactive).
     */
    public Uni<Response> rejectAsync(String note) {
        return resumeAsync(HitlDecision.reject(note));
    }

    /**
     * Reject and resume this paused conversation (blocking, 30s timeout).
     */
    public Response reject(String note) {
        return rejectAsync(note).await().atMost(DEFAULT_TIMEOUT);
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
     * Read the HITL approval status summary of this conversation (blocking, 30s
     * timeout).
     */
    public Response approvalStatus() {
        return approvalStatusAsync("summary").await().atMost(DEFAULT_TIMEOUT);
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
     * The EDDI server runs {@code quarkus.jackson.write-dates-as-timestamps=true}
     * with the {@code JavaTimeModule}, so an {@code Instant} is serialized as a
     * fractional epoch-<em>seconds</em> number (e.g. {@code 1719964800.123000000}),
     * which lands here as a {@link Number} after generic JSON parsing. An ISO-8601
     * string is also accepted as a fallback.
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

    /**
     * Parse the "done" SSE event into a {@link ConversationResult}.
     * <p>
     * The done event data is the full conversation snapshot as JSON. We parse it
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
