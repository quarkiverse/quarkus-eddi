package io.quarkiverse.eddi;

import io.quarkiverse.eddi.client.EddiAgentRestClient;
import io.quarkiverse.eddi.model.ConversationResult;

import java.util.*;

/**
 * Represents an active conversation with an EDDI agent.
 * <p>
 * Provides a fluent interface for sending messages, reading state,
 * and managing the conversation lifecycle.
 * <p>
 * Obtain via {@link EddiClient#agent(String)}:
 * <pre>{@code
 * Conversation conv = eddi.agent("my-agent").startConversation();
 * ConversationResult result = conv.say("Hello!");
 * }</pre>
 */
public class Conversation {

    private final String conversationId;
    private final String agentId;
    private final EddiAgentRestClient restClient;

    Conversation(String conversationId, String agentId, EddiAgentRestClient restClient) {
        this.conversationId = conversationId;
        this.agentId = agentId;
        this.restClient = restClient;
    }

    /**
     * The conversation ID.
     */
    public String id() {
        return conversationId;
    }

    /**
     * The agent ID this conversation is with.
     */
    public String agentId() {
        return agentId;
    }

    /**
     * Send a plain text message and wait for the response.
     */
    public ConversationResult say(String message) {
        var snapshot = restClient.say(
                conversationId, false, true, Collections.emptyList(), message).await().indefinitely();
        return toConversationResult(snapshot);
    }

    /**
     * Send a message with additional context.
     */
    public ConversationResult sayWithContext(String message, Map<String, Object> context) {
        var inputData = new LinkedHashMap<String, Object>();
        inputData.put("input", message);
        inputData.put("context", context);

        var snapshot = restClient.sayWithContext(
                conversationId, false, true, Collections.emptyList(), inputData).await().indefinitely();
        return toConversationResult(snapshot);
    }

    /**
     * End this conversation.
     */
    public void end() {
        restClient.endConversation(conversationId).await().indefinitely();
    }

    /**
     * Undo the last conversation step.
     */
    public void undo() {
        restClient.undo(conversationId).await().indefinitely();
    }

    /**
     * Redo the last undone conversation step.
     */
    public void redo() {
        restClient.redo(conversationId).await().indefinitely();
    }

    /**
     * Read the current conversation state.
     */
    public Map<String, Object> read() {
        return restClient.readConversation(
                conversationId, false, true, Collections.emptyList()).await().indefinitely();
    }

    @SuppressWarnings("unchecked")
    private ConversationResult toConversationResult(Map<String, Object> snapshot) {
        String agentResponse = null;
        List<String> agentResponseParts = List.of();
        List<String> quickReplies = List.of();
        List<String> actions = List.of();
        String conversationState = null;

        var outputs = (List<Map<String, Object>>) snapshot.get("conversationOutputs");
        if (outputs != null && !outputs.isEmpty()) {
            var lastOutput = outputs.get(outputs.size() - 1);

            // Extract agent response text
            var outputItems = lastOutput.get("output");
            if (outputItems instanceof List<?> items) {
                var texts = new ArrayList<String>();
                for (var item : items) {
                    if (item instanceof Map<?, ?> map && map.containsKey("text")) {
                        texts.add(String.valueOf(map.get("text")));
                    }
                }
                if (!texts.isEmpty()) {
                    agentResponse = String.join(" ", texts);
                    agentResponseParts = List.copyOf(texts);
                }
            }

            // Extract quick replies
            var qr = lastOutput.get("quickReplies");
            if (qr instanceof List<?> qrList) {
                var qrValues = new ArrayList<String>();
                for (var item : qrList) {
                    if (item instanceof Map<?, ?> map && map.containsKey("value")) {
                        qrValues.add(String.valueOf(map.get("value")));
                    }
                }
                quickReplies = List.copyOf(qrValues);
            }

            // Extract actions
            var act = lastOutput.get("actions");
            if (act instanceof List<?> actList) {
                actions = actList.stream().map(String::valueOf).toList();
            }
        }

        var state = snapshot.get("conversationState");
        if (state != null) {
            conversationState = state.toString();
        }

        return new ConversationResult(
                conversationId, agentResponse, agentResponseParts,
                quickReplies, actions, conversationState, snapshot);
    }
}
