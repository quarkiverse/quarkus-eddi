package io.quarkiverse.eddi;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.quarkiverse.eddi.model.ConversationResult;
import io.quarkiverse.eddi.model.ConversationState;

/**
 * Unit tests for {@link Conversation} utility methods — snapshot parsing
 * and state parsing logic without requiring a running EDDI server.
 */
class ConversationTest {

    // --- toConversationResult ---

    @Test
    void toConversationResult_nullSnapshot() {
        ConversationResult result = Conversation.toConversationResult(null);
        assertNotNull(result);
        assertNull(result.conversationId());
        assertEquals("", result.text());
        assertTrue(result.agentResponseParts().isEmpty());
        assertTrue(result.quickReplies().isEmpty());
        assertTrue(result.actions().isEmpty());
        assertNull(result.conversationState());
    }

    @Test
    void toConversationResult_emptySnapshot() {
        ConversationResult result = Conversation.toConversationResult(Map.of());
        assertNotNull(result);
        assertNull(result.conversationId());
        assertEquals("", result.text());
    }

    @Test
    void toConversationResult_withConversationId() {
        Map<String, Object> snapshot = Map.of("conversationId", "conv-123");
        ConversationResult result = Conversation.toConversationResult(snapshot);
        assertEquals("conv-123", result.conversationId());
    }

    @Test
    void toConversationResult_withState() {
        Map<String, Object> snapshot = Map.of(
                "conversationId", "conv-1",
                "conversationState", "READY");
        ConversationResult result = Conversation.toConversationResult(snapshot);
        assertEquals(ConversationState.READY, result.conversationState());
    }

    @Test
    void toConversationResult_withOutputs() {
        Map<String, Object> step = Map.of(
                "output", List.of("Hello!", "How can I help?"),
                "quickReplies", List.of("Yes", "No"),
                "actions", List.of("greeting"));

        Map<String, Object> snapshot = Map.of(
                "conversationId", "conv-1",
                "conversationState", "READY",
                "conversationOutputs", List.of(step));

        ConversationResult result = Conversation.toConversationResult(snapshot);
        assertEquals("Hello!\nHow can I help?", result.text());
        assertEquals(2, result.agentResponseParts().size());
        assertEquals(List.of("Yes", "No"), result.quickReplies());
        assertEquals(List.of("greeting"), result.actions());
    }

    @Test
    void toConversationResult_withObjectOutputs_realServerShape() {
        // The real EDDI v6 server serializes output entries as TextOutputItem
        // objects and quick replies as QuickReply objects — NOT plain strings.
        // The parser must extract text/value, not cast to String (which threw).
        Map<String, Object> step = Map.of(
                "output", List.of(
                        Map.of("type", "text", "text", "Hello!", "delay", 0),
                        Map.of("type", "text", "text", "How can I help?", "delay", 0)),
                "quickReplies", List.of(
                        Map.of("value", "Yes", "expressions", "yes", "isDefault", false),
                        Map.of("value", "No", "expressions", "no", "isDefault", false)),
                "actions", List.of("greeting"));

        Map<String, Object> snapshot = Map.of(
                "conversationId", "conv-1",
                "conversationState", "READY",
                "conversationOutputs", List.of(step));

        ConversationResult result = Conversation.toConversationResult(snapshot);
        assertEquals("Hello!\nHow can I help?", result.text());
        assertEquals(2, result.agentResponseParts().size());
        assertEquals(List.of("Yes", "No"), result.quickReplies());
        assertEquals(List.of("greeting"), result.actions());
    }

    @Test
    void toConversationResult_objectOutputWithoutTextKeyIsSkipped() {
        // A non-text output item (e.g. an image) has no "text" key — skip it
        // rather than throwing or emitting a map toString.
        Map<String, Object> step = Map.of(
                "output", List.of(
                        Map.of("type", "image", "uri", "http://x/y.png"),
                        Map.of("type", "text", "text", "caption")));
        Map<String, Object> snapshot = Map.of("conversationOutputs", List.of(step));

        ConversationResult result = Conversation.toConversationResult(snapshot);
        assertEquals("caption", result.text());
        assertEquals(List.of("caption"), result.agentResponseParts());
    }

    @Test
    void toConversationResult_awaitingHumanWithHitlMetadata() {
        Map<String, Object> batch = Map.of(
                "calls", List.of(
                        Map.of("callId", "c1", "toolName", "lookupOrder"),
                        Map.of("callId", "c2", "toolName", "refund")));
        Map<String, Object> snapshot = Map.of(
                "conversationId", "conv-1",
                "conversationState", "AWAITING_HUMAN",
                "hitlPauseType", "TOOL_CALL",
                "hitlPausedAt", "2026-07-05T10:15:30Z",
                "hitlPendingToolCalls", batch,
                "undoAvailable", true,
                "redoAvailable", false);

        ConversationResult result = Conversation.toConversationResult(snapshot);
        assertEquals(ConversationState.AWAITING_HUMAN, result.conversationState());
        assertTrue(result.isAwaitingHuman());
        assertFalse(result.isEnded());
        assertEquals("TOOL_CALL", result.hitlPauseType());
        assertNotNull(result.hitlPausedAt());
        assertEquals(List.of("lookupOrder", "refund"), result.hitlPendingToolNames());
        assertTrue(result.undoAvailable());
        assertFalse(result.redoAvailable());
    }

    @Test
    void toConversationResult_hitlPausedAtFromNumericEpochSeconds() {
        // The server serializes Instant as a fractional epoch-SECONDS number
        // (write-dates-as-timestamps=true), which arrives in the generic snapshot
        // map as a Double — not an ISO string.
        Map<String, Object> snapshot = Map.of(
                "conversationId", "conv-1",
                "conversationState", "AWAITING_HUMAN",
                "hitlPausedAt", 1719964800.123);

        ConversationResult result = Conversation.toConversationResult(snapshot);
        assertNotNull(result.hitlPausedAt(), "numeric epoch-seconds must parse");
        assertEquals(1719964800L, result.hitlPausedAt().getEpochSecond());
        // 0.123s → ~123ms (allow slack for double representation of a ~1.7e9 value)
        long nanos = result.hitlPausedAt().getNano();
        assertTrue(Math.abs(nanos - 123_000_000L) < 1_000L, "nanos=" + nanos);
    }

    @Test
    void toConversationResult_hitlPausedAtFromWholeSecondsLong() {
        Map<String, Object> snapshot = Map.of(
                "conversationId", "conv-1",
                "conversationState", "AWAITING_HUMAN",
                "hitlPausedAt", 1719964800L);
        ConversationResult result = Conversation.toConversationResult(snapshot);
        assertNotNull(result.hitlPausedAt());
        assertEquals(1719964800L, result.hitlPausedAt().getEpochSecond());
        assertEquals(0, result.hitlPausedAt().getNano());
    }

    @Test
    void toConversationResult_noHitlMetadataDefaults() {
        Map<String, Object> snapshot = Map.of(
                "conversationId", "conv-1",
                "conversationState", "READY");
        ConversationResult result = Conversation.toConversationResult(snapshot);
        assertFalse(result.isAwaitingHuman());
        assertNull(result.hitlPauseType());
        assertNull(result.hitlPausedAt());
        assertTrue(result.hitlPendingToolNames().isEmpty());
        assertFalse(result.undoAvailable());
        assertFalse(result.redoAvailable());
    }

    @Test
    void toConversationResult_multipleSteps_usesLatest() {
        Map<String, Object> step1 = Map.of("output", List.of("Old response"));
        Map<String, Object> step2 = Map.of("output", List.of("Latest response"));

        Map<String, Object> snapshot = Map.of(
                "conversationOutputs", List.of(step1, step2));

        ConversationResult result = Conversation.toConversationResult(snapshot);
        assertEquals("Latest response", result.text());
    }

    @Test
    void toConversationResult_emptyOutputsList() {
        Map<String, Object> snapshot = Map.of(
                "conversationOutputs", List.of());

        ConversationResult result = Conversation.toConversationResult(snapshot);
        assertEquals("", result.text());
    }

    @Test
    void toConversationResult_nullFieldsInStep() {
        // Step with no output/quickReplies/actions keys
        Map<String, Object> step = Map.of("someOtherField", "value");

        Map<String, Object> snapshot = Map.of(
                "conversationOutputs", List.of(step));

        ConversationResult result = Conversation.toConversationResult(snapshot);
        assertEquals("", result.text());
        assertTrue(result.agentResponseParts().isEmpty());
        assertTrue(result.quickReplies().isEmpty());
        assertTrue(result.actions().isEmpty());
    }

    // --- parseState ---

    @Test
    void parseState_validStates() {
        assertEquals(ConversationState.READY, Conversation.parseState("READY"));
        assertEquals(ConversationState.IN_PROGRESS, Conversation.parseState("IN_PROGRESS"));
        assertEquals(ConversationState.ERROR, Conversation.parseState("ERROR"));
        assertEquals(ConversationState.ENDED, Conversation.parseState("ENDED"));
        assertEquals(ConversationState.EXECUTION_INTERRUPTED, Conversation.parseState("EXECUTION_INTERRUPTED"));
        assertEquals(ConversationState.AWAITING_HUMAN, Conversation.parseState("AWAITING_HUMAN"));
    }

    @Test
    void parseState_unknownState() {
        assertNull(Conversation.parseState("UNKNOWN_STATE"));
    }

    @Test
    void parseState_lowercaseReturnsNull() {
        // ConversationState.valueOf is case-sensitive
        assertNull(Conversation.parseState("ready"));
    }

    @Test
    void parseState_emptyReturnsNull() {
        assertNull(Conversation.parseState(""));
    }

    // --- ConversationResult immutability ---

    @Test
    void toConversationResult_listsAreUnmodifiable() {
        Map<String, Object> step = Map.of(
                "output", List.of("Hello"),
                "quickReplies", List.of("Yes"));

        Map<String, Object> snapshot = Map.of(
                "conversationOutputs", List.of(step));

        ConversationResult result = Conversation.toConversationResult(snapshot);
        assertThrows(UnsupportedOperationException.class,
                () -> result.agentResponseParts().add("injected"));
        assertThrows(UnsupportedOperationException.class,
                () -> result.quickReplies().add("injected"));
    }
}
