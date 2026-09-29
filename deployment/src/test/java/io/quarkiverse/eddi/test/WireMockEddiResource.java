package io.quarkiverse.eddi.test;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

import java.util.Map;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

/**
 * WireMock test resource that simulates an EDDI v6 server.
 * <p>
 * Provides stubs for the core EDDI API endpoints used by the SDK:
 * start conversation, say, end conversation, health probe, and admin status.
 */
public class WireMockEddiResource implements QuarkusTestResourceLifecycleManager {

    private WireMockServer wireMockServer;

    @Override
    public Map<String, String> start() {
        wireMockServer = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMockServer.start();
        WireMock.configureFor(wireMockServer.port());

        setupStubs();

        return Map.of(
                "quarkus.rest-client.eddi.url", wireMockServer.baseUrl(),
                "quarkus.eddi.url", wireMockServer.baseUrl(),
                "wiremock.port", String.valueOf(wireMockServer.port()));
    }

    private void setupStubs() {
        // --- Start conversation ---
        // POST /agents/{agentId}/start → 201 with Location header
        stubFor(post(urlPathMatching("/agents/[^/]+/start"))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Location", wireMockServer.baseUrl() + "/agents/conv-wiremock-001")));

        // --- Say (text/plain) ---
        // POST /agents/{conversationId} with Content-Type: text/plain → 200 with snapshot.
        // NOTE: output items are serialized as TextOutputItem objects and quick
        // replies as QuickReply objects — the real EDDI v6 wire shape (not plain
        // strings) — so this stub exercises the SDK's snapshot parser correctly.
        stubFor(post(urlPathEqualTo("/agents/conv-wiremock-001"))
                .withHeader("Content-Type", containing("text/plain"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                    "conversationId": "conv-wiremock-001",
                                    "conversationState": "READY",
                                    "conversationOutputs": [
                                        {
                                            "output": [
                                                {"type": "text", "text": "Hello from WireMock! How can I help you?", "delay": 0}
                                            ],
                                            "quickReplies": [
                                                {"value": "Yes", "expressions": "yes", "isDefault": false},
                                                {"value": "No", "expressions": "no", "isDefault": false}
                                            ],
                                            "actions": ["greeting"]
                                        }
                                    ]
                                }
                                """)));

        // --- Say within context (application/json) ---
        stubFor(post(urlPathEqualTo("/agents/conv-wiremock-001"))
                .withHeader("Content-Type", containing("application/json"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                    "conversationId": "conv-wiremock-001",
                                    "conversationState": "READY",
                                    "conversationOutputs": [
                                        {
                                            "output": [
                                                {"type": "text", "text": "Context received!", "delay": 0}
                                            ],
                                            "quickReplies": [],
                                            "actions": []
                                        }
                                    ]
                                }
                                """)));

        // --- End conversation ---
        stubFor(post(urlPathEqualTo("/agents/conv-wiremock-001/endConversation"))
                .willReturn(aResponse().withStatus(200)));

        // --- Conversation state ---
        stubFor(get(urlPathEqualTo("/agents/conv-wiremock-001/status"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("\"READY\"")));

        // --- Health check probe (404 = server is up) ---
        stubFor(get(urlPathEqualTo("/agents/health-check-probe/status"))
                .willReturn(aResponse().withStatus(404)));

        // --- Admin: deployment statuses ---
        stubFor(get(urlPathMatching("/administration/[^/]+/deploymentstatus"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("[]")));

        // --- Coordinator status ---
        stubFor(get(urlPathEqualTo("/administration/coordinator/status"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                    "coordinatorType": "in-memory",
                                    "connected": true,
                                    "connectionStatus": "OK",
                                    "activeConversations": 0,
                                    "totalProcessed": 0,
                                    "totalDeadLettered": 0,
                                    "queueDepths": {}
                                }
                                """)));

        // --- Logs ---
        stubFor(get(urlPathEqualTo("/administration/logs"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("[]")));

        // --- SSE Streaming ---
        // POST /agents/conv-wiremock-001/stream with text/plain → SSE events
        stubFor(post(urlPathEqualTo("/agents/conv-wiremock-001/stream"))
                .withHeader("Content-Type", containing("text/plain"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/event-stream")
                        .withBody("""
                                event: task_start
                                data: {"taskId":"1","taskType":"llm","index":0}

                                event: token
                                data: Hello

                                event: token
                                data:  from

                                event: token
                                data:  WireMock!

                                event: task_complete
                                data: {"taskId":"1","taskType":"llm","durationMs":42}

                                event: done
                                data: {"conversationId":"conv-wiremock-001","conversationState":"READY"}

                                """)));

        // ═══════════════════════════════════════════
        //  HITL (Human-in-the-Loop) stubs
        // ═══════════════════════════════════════════

        // Start a conversation with the HITL agent → conv-hitl-001
        stubFor(post(urlPathEqualTo("/agents/hitl-agent/start"))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Location", wireMockServer.baseUrl() + "/agents/conv-hitl-001")));

        // Say to the HITL conversation → server pauses awaiting a human decision
        stubFor(post(urlPathEqualTo("/agents/conv-hitl-001"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                    "conversationId": "conv-hitl-001",
                                    "conversationState": "AWAITING_HUMAN",
                                    "hitlPauseType": "TOOL_CALL",
                                    "hitlPausedAt": 1719964800.123,
                                    "hitlPendingToolCalls": {
                                        "calls": [
                                            {"callId": "c1", "toolName": "lookupOrder"}
                                        ]
                                    },
                                    "undoAvailable": false,
                                    "redoAvailable": false,
                                    "conversationOutputs": [
                                        {
                                            "output": [
                                                {"type": "text", "text": "I need approval to look up your order.", "delay": 0}
                                            ],
                                            "quickReplies": [],
                                            "actions": []
                                        }
                                    ]
                                }
                                """)));

        // Status of the paused conversation → AWAITING_HUMAN (must deserialize,
        // which fails if the SDK enum lacks the constant).
        stubFor(get(urlPathEqualTo("/agents/conv-hitl-001/status"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("\"AWAITING_HUMAN\"")));

        // Approval status summary
        stubFor(get(urlPathEqualTo("/agents/conv-hitl-001/approval-status"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                    "conversationId": "conv-hitl-001",
                                    "conversationState": "AWAITING_HUMAN",
                                    "pauseType": "TOOL_CALL"
                                }
                                """)));

        // Resume with a human decision → 200, conversation advances to READY
        stubFor(post(urlPathEqualTo("/agents/conv-hitl-001/resume"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                    "conversationId": "conv-hitl-001",
                                    "conversationState": "READY"
                                }
                                """)));

        // Cancel the paused conversation → 200
        stubFor(post(urlPathEqualTo("/agents/conv-hitl-001/cancel"))
                .willReturn(aResponse().withStatus(200)));

        // End stub for the HITL conversation — present so an ERRANT end() is
        // recorded (and asserted against) rather than 404-ing. A correct client
        // never ends an AWAITING_HUMAN conversation.
        stubFor(post(urlPathEqualTo("/agents/conv-hitl-001/endConversation"))
                .willReturn(aResponse().withStatus(200)));

        // Pending approvals inbox (single-agent)
        stubFor(get(urlPathEqualTo("/agents/pending-approvals"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                [
                                    {
                                        "conversationId": "conv-hitl-001",
                                        "agentId": "hitl-agent",
                                        "userId": "user-1",
                                        "pausedAt": 1719964800.123,
                                        "pauseReason": "tool approval required",
                                        "timeoutPolicy": "REJECT",
                                        "pauseType": "TOOL_CALL",
                                        "toolNames": ["lookupOrder"]
                                    }
                                ]
                                """)));

        // Cross-group HITL inbox
        stubFor(get(urlPathEqualTo("/groups/pending-approvals"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("[]")));

        // --- Error scenarios ---
        // Start conversation with non-existent agent → 404
        stubFor(post(urlPathEqualTo("/agents/non-existent-agent/start"))
                .willReturn(aResponse()
                        .withStatus(404)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":\"Agent not found\"}")));

        // Simulate server error for a specific conversation
        stubFor(post(urlPathEqualTo("/agents/error-conv/start"))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":\"Internal server error\"}")));
    }

    @Override
    public void stop() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }
}
