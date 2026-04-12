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
        // POST /agents/{conversationId} with Content-Type: text/plain → 200 with snapshot
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
                                            "output": ["Hello from WireMock! How can I help you?"],
                                            "quickReplies": ["Yes", "No"],
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
                                            "output": ["Context received!"],
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
    }

    @Override
    public void stop() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }
}
