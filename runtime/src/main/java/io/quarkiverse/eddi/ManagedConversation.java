package io.quarkiverse.eddi;

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

    private final String intent;
    private final String userId;
    private final EddiManagedRestClient managedClient;
    private final java.time.Duration timeout;

    /**
     * Package-private constructor — managed conversations should be created via
     * {@link EddiClient.ManagedAgentBuilder#build()}.
     */
    ManagedConversation(String intent, String userId, EddiManagedRestClient managedClient) {
        this(intent, userId, managedClient, EddiDefaults.DEFAULT_TIMEOUT);
    }

    ManagedConversation(String intent, String userId, EddiManagedRestClient managedClient,
            java.time.Duration timeout) {
        this.timeout = timeout;
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
     * Send a text message (blocking, up to quarkus.eddi.timeout).
     */
    public ConversationResult say(String message) {
        return sayAsync(message).await().atMost(timeout);
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
     * Load the current conversation memory (blocking, up to quarkus.eddi.timeout).
     */
    public ConversationResult load() {
        return loadAsync().await().atMost(timeout);
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
     * End the current conversation (blocking, up to quarkus.eddi.timeout).
     */
    public void end() {
        endAsync().await().atMost(timeout);
    }
}
