package io.quarkiverse.eddi;

import java.time.Duration;

import jakarta.ws.rs.core.Response;

import io.quarkiverse.eddi.client.EddiManagedRestClient;
import io.quarkiverse.eddi.model.*;
import io.smallrye.mutiny.Uni;

/**
 * Intent-based conversation wrapper using EDDI v6's managed agent API.
 * <p>
 * No conversation ID needed — addressed by intent + userId pair.
 * EDDI automatically resolves the active conversation.
 * <p>
 * Example:
 *
 * <pre>{@code
 * ManagedConversation mc = eddi.managed("support").userId("user-1").build();
 * ConversationResult result = mc.say("I need help with my order");
 * mc.end();
 * }</pre>
 */
public class ManagedConversation {

    /** Default timeout for blocking operations (30 seconds). */
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

    private final String intent;
    private final String userId;
    private final EddiManagedRestClient managedClient;

    public ManagedConversation(String intent, String userId, EddiManagedRestClient managedClient) {
        this.intent = intent;
        this.userId = userId;
        this.managedClient = managedClient;
    }

    public String intent() {
        return intent;
    }

    public String userId() {
        return userId;
    }

    // ─── Say (reactive) ───────────────────────────

    /**
     * Send a text message (reactive).
     */
    public Uni<ConversationResult> sayAsync(String message) {
        return sayAsync(InputData.of(message));
    }

    /**
     * Send structured input (reactive).
     */
    public Uni<ConversationResult> sayAsync(InputData inputData) {
        return managedClient.sayWithinContext(intent, userId, false, true, null, inputData)
                .map(Conversation::toConversationResult);
    }

    // ─── Say (blocking) ───────────────────────────

    /**
     * Send a text message (blocking, 30s timeout).
     */
    public ConversationResult say(String message) {
        return sayAsync(message).await().atMost(DEFAULT_TIMEOUT);
    }

    // ─── Load ─────────────────────────────────────

    /**
     * Load the current conversation memory (reactive).
     */
    public Uni<ConversationResult> loadAsync() {
        return managedClient.loadConversationMemory(intent, userId, null, false, true, null)
                .map(Conversation::toConversationResult);
    }

    /**
     * Load the current conversation memory (blocking, 30s timeout).
     */
    public ConversationResult load() {
        return loadAsync().await().atMost(DEFAULT_TIMEOUT);
    }

    // ─── Undo / Redo ──────────────────────────────

    public Uni<Boolean> isUndoAvailableAsync() {
        return managedClient.isUndoAvailable(intent, userId);
    }

    public Uni<Response> undoAsync() {
        return managedClient.undo(intent, userId);
    }

    public Uni<Boolean> isRedoAvailableAsync() {
        return managedClient.isRedoAvailable(intent, userId);
    }

    public Uni<Response> redoAsync() {
        return managedClient.redo(intent, userId);
    }

    // ─── End ──────────────────────────────────────

    /**
     * End the current conversation (reactive).
     */
    public Uni<Response> endAsync() {
        return managedClient.endCurrentConversation(intent, userId);
    }

    /**
     * End the current conversation (blocking, 30s timeout).
     */
    public void end() {
        endAsync().await().atMost(DEFAULT_TIMEOUT);
    }
}
