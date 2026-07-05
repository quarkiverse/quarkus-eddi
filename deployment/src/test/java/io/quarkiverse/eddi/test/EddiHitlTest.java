package io.quarkiverse.eddi.test;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import io.quarkiverse.eddi.Conversation;
import io.quarkiverse.eddi.EddiClient;
import io.quarkiverse.eddi.model.ConversationResult;
import io.quarkiverse.eddi.model.ConversationState;
import io.quarkiverse.eddi.model.HitlDecision;
import io.quarkiverse.eddi.model.PendingApprovalSummary;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;

/**
 * End-to-end HITL (Human-in-the-Loop) tests against a WireMock-simulated EDDI v6
 * server. Exercises the release-critical paths the SDK previously could not
 * support: deserializing the {@code AWAITING_HUMAN} state, reading pause
 * metadata, resuming a paused conversation, and the approvals inbox.
 */
@QuarkusTest
@QuarkusTestResource(WireMockEddiResource.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EddiHitlTest {

    @Inject
    EddiClient eddi;

    @Test
    @Order(1)
    void sayPausesAwaitingHumanWithMetadata() {
        Conversation conv = eddi.agent("hitl-agent").startConversation();
        assertEquals("conv-hitl-001", conv.id());

        ConversationResult result = conv.say("Look up my order");
        assertTrue(result.isAwaitingHuman(), "conversation should be paused awaiting a human");
        assertEquals(ConversationState.AWAITING_HUMAN, result.conversationState());
        assertEquals("TOOL_CALL", result.hitlPauseType());
        assertNotNull(result.hitlPausedAt());
        assertEquals(List.of("lookupOrder"), result.hitlPendingToolNames());
        // The agent's text still parses from the TextOutputItem object shape.
        assertEquals("I need approval to look up your order.", result.text());
    }

    @Test
    @Order(2)
    void statusOfPausedConversationDeserializes() {
        // Before AWAITING_HUMAN was added to the SDK enum, this call threw
        // InvalidFormatException on the raw enum. It must now round-trip.
        Conversation conv = eddi.agent("hitl-agent").conversation("conv-hitl-001");
        assertEquals(ConversationState.AWAITING_HUMAN, conv.state());
        assertTrue(conv.isAwaitingHuman());
    }

    @Test
    @Order(3)
    void resumeApprovesPausedConversation() {
        Conversation conv = eddi.agent("hitl-agent").conversation("conv-hitl-001");
        try (Response response = conv.approve("looks good")) {
            assertEquals(200, response.getStatus());
        }
        // Prove the SDK actually serialized and sent the decision body through the
        // REST-client marshalling path — not just that a 200 endpoint was reached.
        verify(postRequestedFor(urlPathEqualTo("/agents/conv-hitl-001/resume"))
                .withRequestBody(matchingJsonPath("$.verdict", equalTo("APPROVED")))
                .withRequestBody(matchingJsonPath("$.note", equalTo("looks good"))));
    }

    @Test
    @Order(4)
    void resumeWithExplicitDecision() {
        Conversation conv = eddi.agent("hitl-agent").conversation("conv-hitl-001");
        try (Response response = conv.resume(HitlDecision.reject("not allowed"))) {
            assertEquals(200, response.getStatus());
        }
        verify(postRequestedFor(urlPathEqualTo("/agents/conv-hitl-001/resume"))
                .withRequestBody(matchingJsonPath("$.verdict", equalTo("REJECTED")))
                .withRequestBody(matchingJsonPath("$.note", equalTo("not allowed"))));
    }

    @Test
    @Order(5)
    void approvalStatusIsReadable() {
        Conversation conv = eddi.agent("hitl-agent").conversation("conv-hitl-001");
        try (Response response = conv.approvalStatus()) {
            assertEquals(200, response.getStatus());
        }
    }

    @Test
    @Order(6)
    void pendingApprovalsInboxLists() {
        List<PendingApprovalSummary> pending = eddi.approvals().pending();
        assertEquals(1, pending.size());
        PendingApprovalSummary summary = pending.get(0);
        assertEquals("conv-hitl-001", summary.conversationId());
        assertEquals("hitl-agent", summary.agentId());
        assertTrue(summary.isToolCallPause());
        assertEquals(List.of("lookupOrder"), summary.toolNames());
        assertNotNull(summary.pausedAt());
    }

    @Test
    @Order(7)
    void crossGroupApprovalsInboxIsReachable() {
        // Structurally verifies the /groups (root) client path — the cross-group
        // inbox route would be unreachable under a /groups/{groupId}/conversations
        // class path.
        assertNotNull(eddi.approvals().pendingGroups());
    }

    @Test
    @Order(8)
    void chatFullDoesNotEndPausedConversation() {
        // The one-liner must leave an AWAITING_HUMAN conversation open (ending it
        // would cancel the pending approval).
        ConversationResult result = eddi.chatFull("hitl-agent", "Look up my order");
        assertTrue(result.isAwaitingHuman());
        assertEquals("conv-hitl-001", result.conversationId());
        // Guard the actual behavior: the paused conversation must NEVER be ended.
        // (endQuietly swallows errors, so asserting only on the returned value would
        // not catch a regression that erroneously ends it.)
        verify(0, postRequestedFor(urlPathEqualTo("/agents/conv-hitl-001/endConversation")));
    }

    @Test
    @Order(9)
    void cancelPausedConversation() {
        Conversation conv = eddi.agent("hitl-agent").conversation("conv-hitl-001");
        assertDoesNotThrow(conv::cancel);
    }
}
