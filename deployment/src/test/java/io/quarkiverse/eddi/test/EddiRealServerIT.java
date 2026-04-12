package io.quarkiverse.eddi.test;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.net.HttpURLConnection;
import java.net.URI;
import java.util.Map;
import java.util.logging.Logger;

import jakarta.inject.Inject;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import io.quarkiverse.eddi.Conversation;
import io.quarkiverse.eddi.EddiClient;
import io.quarkiverse.eddi.model.ConversationResult;
import io.quarkiverse.eddi.model.CoordinatorStatus;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;

/**
 * Integration tests against a <b>real EDDI server</b> running at localhost:7070.
 * <p>
 * These tests are <b>automatically skipped</b> if EDDI is not reachable.
 * When EDDI is running, they prove end-to-end API contract compatibility
 * between the SDK and the actual EDDI v6 server.
 * <p>
 * To run these tests, start EDDI locally:
 *
 * <pre>{@code
 * cd ../EDDI && ./mvnw compile quarkus:dev
 * }</pre>
 */
@QuarkusTest
@TestProfile(EddiRealServerIT.RealEddiProfile.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EddiRealServerIT {

    private static final Logger LOG = Logger.getLogger(EddiRealServerIT.class.getName());
    private static final String EDDI_URL = "http://localhost:7070";
    private static boolean eddiAvailable = false;

    @Inject
    EddiClient eddi;

    /**
     * Quarkus test profile that points the REST client to the real EDDI server.
     */
    public static class RealEddiProfile implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of(
                    "quarkus.rest-client.eddi.url", EDDI_URL,
                    "quarkus.eddi.url", EDDI_URL,
                    "quarkus.eddi.devservices.enabled", "false");
        }
    }

    @BeforeAll
    static void checkEddiAvailability() {
        try {
            var conn = (HttpURLConnection) URI.create(EDDI_URL + "/q/health").toURL().openConnection();
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);
            int code = conn.getResponseCode();
            eddiAvailable = (code >= 200 && code < 500);
            conn.disconnect();
        } catch (Exception e) {
            eddiAvailable = false;
        }
        if (!eddiAvailable) {
            LOG.info("EDDI not running at " + EDDI_URL + " -- skipping real server integration tests");
        }
    }

    // ═══════════════════════════════════════════════
    //  Connectivity
    // ═══════════════════════════════════════════════

    @Test
    @Order(1)
    void eddiServerIsReachable() {
        assumeTrue(eddiAvailable, "EDDI not running at " + EDDI_URL);
        assertNotNull(eddi);
    }

    // ═══════════════════════════════════════════════
    //  Admin API
    // ═══════════════════════════════════════════════

    @Test
    @Order(10)
    void listDeployedAgents() {
        assumeTrue(eddiAvailable, "EDDI not running");
        var statuses = eddi.admin().listDeployed();
        assertNotNull(statuses);
        LOG.info("Deployed agents: " + statuses.size());
        statuses.forEach(s -> LOG.info("  - " + s.agentId() + " v" + s.agentVersion()
                + " [" + s.status() + "] " + s.descriptorName()));
    }

    // ═══════════════════════════════════════════════
    //  Coordinator API
    // ═══════════════════════════════════════════════

    @Test
    @Order(20)
    void coordinatorStatusIsReadable() {
        assumeTrue(eddiAvailable, "EDDI not running");
        CoordinatorStatus status = eddi.coordinator().status();
        assertNotNull(status);
        assertNotNull(status.coordinatorType());
        LOG.info("Coordinator: " + status.coordinatorType()
                + " connected=" + status.connected()
                + " processed=" + status.totalProcessed());
    }

    // ═══════════════════════════════════════════════
    //  Logs API
    // ═══════════════════════════════════════════════

    @Test
    @Order(30)
    void recentLogsAreReadable() {
        assumeTrue(eddiAvailable, "EDDI not running");
        var logs = eddi.logs().recent();
        assertNotNull(logs);
        LOG.info("Recent logs: " + logs.size());
    }

    // ═══════════════════════════════════════════════
    //  Conversation (if agents are deployed)
    // ═══════════════════════════════════════════════

    @Test
    @Order(40)
    void chatWithDeployedAgent() {
        assumeTrue(eddiAvailable, "EDDI not running");

        var statuses = eddi.admin().listDeployed();
        if (statuses.isEmpty()) {
            LOG.warning("No agents deployed -- skipping chat test");
            return;
        }

        // Chat with the first deployed agent
        String agentId = statuses.get(0).agentId();
        LOG.info("Chatting with agent: " + agentId);

        Conversation conv = eddi.agent(agentId).startConversation();
        assertNotNull(conv);
        assertNotNull(conv.id());
        LOG.info("Conversation started: " + conv.id());

        try {
            ConversationResult result = conv.say("Hello from the SDK integration test!");
            assertNotNull(result);
            LOG.info("Agent response: " + result.text());
            LOG.info("State: " + result.conversationState());
            LOG.info("Quick replies: " + result.quickReplies());
        } finally {
            try {
                conv.end();
            } catch (Exception ignored) {
                // Best-effort cleanup
            }
            LOG.info("Conversation ended.");
        }
    }
}
