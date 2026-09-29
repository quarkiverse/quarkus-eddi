package io.quarkiverse.eddi.test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.*;

import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import io.quarkiverse.eddi.Conversation;
import io.quarkiverse.eddi.EddiClient;
import io.quarkiverse.eddi.model.ConversationState;
import io.quarkiverse.eddi.model.CoordinatorStatus;
import io.quarkiverse.eddi.model.SetupResult;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;

/**
 * Boots a real EDDI server + MongoDB through the extension's Dev Services and
 * exercises the SDK against it.
 * <p>
 * Opt-in because it needs Docker with Linux containers and pulls the EDDI image:
 *
 * <pre>{@code
 * mvn verify -Deddi.it.devservices=true
 * }</pre>
 */
@QuarkusTest
@TestProfile(EddiDevServicesIT.DevServicesProfile.class)
@EnabledIfSystemProperty(named = "eddi.it.devservices", matches = "true")
class EddiDevServicesIT {

    /** Uses its own config profile so the %test WireMock URLs and Dev Services opt-out don't apply. */
    public static class DevServicesProfile implements QuarkusTestProfile {
        @Override
        public String getConfigProfile() {
            return "devservices-it";
        }

        /** The WireMock test resource is global; it must not override the Dev Services URL. */
        @Override
        public boolean disableGlobalTestResources() {
            return true;
        }
    }

    @Inject
    EddiClient eddi;

    @ConfigProperty(name = "quarkus.eddi.url")
    String eddiUrl;

    @Test
    void devServicesStartedEddiContainer() {
        assertFalse(eddiUrl.endsWith(":7070"), "should point at the container's mapped port, was " + eddiUrl);
        // Only a real EDDI answers this (WireMock has no stub for it)
        String instanceId = eddi.logs().instanceIdAsync().await().atMost(java.time.Duration.ofSeconds(30))
                .get("instanceId");
        assertNotNull(instanceId);
        assertFalse(instanceId.isBlank());
    }

    @Test
    void readinessProbeReportsEddiUp() {
        given()
                .when().get("/q/health/ready")
                .then()
                .statusCode(200)
                .body("checks.find { it.name == 'EDDI' }.status", equalTo("UP"));
    }

    @Test
    void adminApisRespond() {
        assertNotNull(eddi.admin().listDeployed());
        CoordinatorStatus status = eddi.coordinator().status();
        assertNotNull(status.coordinatorType());
        assertNotNull(eddi.logs().recent());
        assertNotNull(eddi.approvals().pending());
    }

    @Test
    void setupAgentAndRunConversationLifecycle() {
        // No LLM call happens until a message is sent, so a placeholder key is fine.
        SetupResult setup = eddi.setup()
                .agentName("SDK IT Agent")
                .systemPrompt("You are a test agent.")
                .provider("openai").model("gpt-4o-mini").apiKey("sk-placeholder")
                .deploy(true)
                .create();
        assertNotNull(setup.agentId());
        assertTrue(Boolean.TRUE.equals(setup.deployed()), "deployed: " + setup);

        Conversation conv = eddi.agent(setup.agentId()).userId("it-user").startConversation();
        assertNotNull(conv.id());
        assertEquals(ConversationState.READY, conv.state());

        // Real SSE framing: the placeholder key makes the LLM task fail, but the
        // stream still carries lifecycle events and ends with done or error.
        java.util.List<io.quarkiverse.eddi.model.StreamToken> events = conv.sayStreaming("Hello")
                .collect().asList()
                .onFailure().recoverWithItem(java.util.List.of())
                .await().atMost(java.time.Duration.ofMinutes(2));
        assertTrue(events.stream().anyMatch(e -> e.isTaskStart()), "expected task_start: " + events);
        var last = events.get(events.size() - 1);
        assertTrue(last.isDone() || last.isError(), "expected terminal event: " + events);

        // A failed turn leaves the conversation in ERROR; the admin reset brings it back.
        if (conv.state() == ConversationState.ERROR) {
            conv.resetState();
            assertEquals(ConversationState.READY, conv.state());
        }

        conv.end();
        assertEquals(ConversationState.ENDED, conv.state());
    }
}
