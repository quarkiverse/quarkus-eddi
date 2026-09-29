package io.quarkiverse.eddi.test;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.patchRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.github.tomakehurst.wiremock.client.WireMock;

import io.quarkiverse.eddi.EddiClient;
import io.quarkiverse.eddi.client.EddiGroupRestClient.AttachmentRef;
import io.quarkiverse.eddi.model.HitlDecision;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Verifies the group-conversation lifecycle endpoints added for EDDI 6.4 hit the
 * server's exact paths with the server's request body shapes.
 */
@QuarkusTest
@QuarkusTestResource(WireMockEddiResource.class)
class EddiGroupTest {

    @Inject
    EddiClient eddi;

    @ConfigProperty(name = "wiremock.port")
    int wireMockPort;

    WireMock wireMock;

    @BeforeEach
    void bindWireMockClient() {
        wireMock = new WireMock(wireMockPort);
    }

    @Test
    void followUpTargetsMember() {
        try (Response response = eddi.group("group-1").userId("u1").followUp("gc-1", "Rosa", "Why?")) {
            assertEquals(200, response.getStatus());
        }
        wireMock.verifyThat(postRequestedFor(urlPathEqualTo("/groups/group-1/conversations/gc-1/followup"))
                .withRequestBody(matchingJsonPath("$.question", equalTo("Why?")))
                .withRequestBody(matchingJsonPath("$.targetAgentId", equalTo("Rosa")))
                .withRequestBody(matchingJsonPath("$.userId", equalTo("u1"))));
    }

    @Test
    void continueDiscussionSendsNewQuestionWithoutAttachments() {
        eddi.group("group-1")
                .attachments(List.of(AttachmentRef.url("https://example.com/a.pdf")))
                .continueDiscussionAsync("gc-1", "Round two?").await().indefinitely().close();

        // Attachments are only valid when a discussion starts; the server 400s them here.
        wireMock.verifyThat(postRequestedFor(urlPathEqualTo("/groups/group-1/conversations/gc-1/continue"))
                .withRequestBody(matchingJsonPath("$.question", equalTo("Round two?")))
                .withRequestBody(WireMock.notContaining("attachments")));
    }

    @Test
    void closeAndHumanInput() {
        eddi.group("group-1").close("gc-1");
        wireMock.verifyThat(postRequestedFor(urlPathEqualTo("/groups/group-1/conversations/gc-1/close")));

        try (Response response = eddi.group("group-1").submitHumanInput("gc-1", "alice", "I agree")) {
            assertEquals(200, response.getStatus());
        }
        wireMock.verifyThat(postRequestedFor(urlPathEqualTo("/groups/group-1/conversations/gc-1/human-input"))
                .withRequestBody(matchingJsonPath("$.memberId", equalTo("alice")))
                .withRequestBody(matchingJsonPath("$.content", equalTo("I agree"))));
    }

    @Test
    void resetStateTargetsReady() {
        eddi.agent("any").conversation("conv-wiremock-001").resetState();
        wireMock.verifyThat(patchRequestedFor(urlPathEqualTo("/agents/conv-wiremock-001/state"))
                .withQueryParam("state", equalTo("READY")));
    }

    @Test
    void approveSendsDecisionAndPendingApprovalsUsesServerDefaults() {
        try (Response response = eddi.group("group-1").approve("gc-1", HitlDecision.approve("ship it"))) {
            assertEquals(200, response.getStatus());
        }
        wireMock.verifyThat(postRequestedFor(urlPathEqualTo("/groups/group-1/conversations/gc-1/approve"))
                .withRequestBody(matchingJsonPath("$.decision.verdict", equalTo("APPROVED")))
                .withRequestBody(matchingJsonPath("$.decision.note", equalTo("ship it"))));

        assertTrue(eddi.approvals().pendingGroups().isEmpty());
        wireMock.verifyThat(getRequestedFor(urlPathEqualTo("/groups/pending-approvals"))
                .withQueryParam("limit", equalTo("100")));
    }

    @Test
    void chatEndsTheConversationItStarted() {
        wireMock.resetRequests();
        assertFalse(eddi.chat("any-agent", "Hello").isBlank());
        wireMock.verifyThat(1, postRequestedFor(urlPathEqualTo("/agents/conv-wiremock-001/endConversation")));
    }
}
