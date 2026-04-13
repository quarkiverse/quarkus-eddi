package io.quarkiverse.eddi.test;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import jakarta.inject.Inject;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import io.quarkiverse.eddi.Conversation;
import io.quarkiverse.eddi.EddiClient;
import io.quarkiverse.eddi.model.ConversationResult;
import io.quarkiverse.eddi.model.ConversationState;
import io.quarkiverse.eddi.model.CoordinatorStatus;
import io.quarkiverse.eddi.model.LogEntry;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Integration tests that boot the full Quarkus EDDI extension and verify
 * end-to-end SDK functionality against a WireMock-simulated EDDI server.
 * <p>
 * These tests prove:
 * <ul>
 * <li>CDI injection of {@link EddiClient} works</li>
 * <li>REST client wiring resolves correctly</li>
 * <li>The full chat() flow (start → say → parse → end) works</li>
 * <li>Conversation lifecycle management works</li>
 * <li>Admin/coordinator/log API facades work</li>
 * </ul>
 */
@QuarkusTest
@QuarkusTestResource(WireMockEddiResource.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EddiExtensionTest {

    @Inject
    EddiClient eddi;

    // ═══════════════════════════════════════════════
    //  Boot & injection
    // ═══════════════════════════════════════════════

    @Test
    @Order(1)
    void eddiClientIsInjectable() {
        assertNotNull(eddi, "EddiClient should be injectable as a CDI bean");
    }

    // ═══════════════════════════════════════════════
    //  One-liner API
    // ═══════════════════════════════════════════════

    @Test
    @Order(10)
    void chatReturnsTextResponse() {
        String response = eddi.chat("test-agent", "Hello!");
        assertNotNull(response);
        assertFalse(response.isBlank(), "chat() should return a non-blank response");
        assertTrue(response.contains("WireMock"), "Response should come from WireMock stub");
    }

    @Test
    @Order(11)
    void chatFullReturnsStructuredResult() {
        ConversationResult result = eddi.chatFull("test-agent", "Hello!");
        assertNotNull(result);
        assertEquals("conv-wiremock-001", result.conversationId());
        assertEquals(ConversationState.READY, result.conversationState());
        assertFalse(result.agentResponseParts().isEmpty());
        assertEquals(List.of("Yes", "No"), result.quickReplies());
        assertEquals(List.of("greeting"), result.actions());
    }

    @Test
    @Order(12)
    void chatAsyncReturnsResult() {
        ConversationResult result = eddi.chatAsync("test-agent", "Hello!")
                .await().indefinitely();
        assertNotNull(result);
        assertEquals("conv-wiremock-001", result.conversationId());
    }

    // ═══════════════════════════════════════════════
    //  Full conversation lifecycle
    // ═══════════════════════════════════════════════

    @Test
    @Order(20)
    void conversationLifecycle() {
        // Start
        Conversation conv = eddi.agent("test-agent").startConversation();
        assertNotNull(conv);
        assertEquals("conv-wiremock-001", conv.id());

        // Say
        ConversationResult result = conv.say("Hello!");
        assertNotNull(result);
        assertEquals("Hello from WireMock! How can I help you?", result.text());
        assertFalse(result.isEnded());

        // End
        assertDoesNotThrow(() -> conv.end());
    }

    @Test
    @Order(21)
    void conversationStartAsync() {
        Conversation conv = eddi.agent("test-agent")
                .startConversationAsync()
                .await().indefinitely();
        assertNotNull(conv);
        assertEquals("conv-wiremock-001", conv.id());
        assertEquals("test-agent", conv.agentId());
    }

    // ═══════════════════════════════════════════════
    //  Admin / Coordinator / Logs facades
    // ═══════════════════════════════════════════════

    @Test
    @Order(30)
    void adminListDeployed() {
        var statuses = eddi.admin().listDeployed();
        assertNotNull(statuses);
        // WireMock returns empty list
        assertTrue(statuses.isEmpty());
    }

    @Test
    @Order(31)
    void coordinatorStatus() {
        CoordinatorStatus status = eddi.coordinator().status();
        assertNotNull(status);
        assertEquals("in-memory", status.coordinatorType());
        assertTrue(status.connected());
    }

    @Test
    @Order(32)
    void logsRecent() {
        List<LogEntry> logs = eddi.logs().recent();
        assertNotNull(logs);
        // WireMock returns empty list
        assertTrue(logs.isEmpty());
    }

    // ═══════════════════════════════════════════════
    //  Error handling (negative paths)
    // ═══════════════════════════════════════════════

    @Test
    @Order(40)
    void startConversationWithNonExistentAgentThrows() {
        assertThrows(Exception.class, () -> eddi.agent("non-existent-agent").startConversation(),
                "Starting a conversation with a non-existent agent should throw");
    }

    @Test
    @Order(41)
    void startConversationServerErrorThrows() {
        assertThrows(Exception.class, () -> eddi.agent("error-conv").startConversation(),
                "Server 500 error should propagate as an exception");
    }

    @Test
    @Order(42)
    void chatWithNonExistentAgentThrows() {
        assertThrows(Exception.class, () -> eddi.chat("non-existent-agent", "Hello"),
                "chat() with a non-existent agent should throw");
    }

    @Test
    @Order(43)
    void chatAsyncWithNonExistentAgentFails() {
        assertThrows(Exception.class,
                () -> eddi.chatAsync("non-existent-agent", "Hello").await().indefinitely(),
                "chatAsync() with a non-existent agent should fail");
    }
}
