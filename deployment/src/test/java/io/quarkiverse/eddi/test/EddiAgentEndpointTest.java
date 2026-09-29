package io.quarkiverse.eddi.test;

import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

import com.github.tomakehurst.wiremock.client.WireMock;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Integration test for {@code @EddiAgent} build-time endpoint generation.
 * <p>
 * Verifies that the Gizmo-generated resource (emitted through Quarkus REST's
 * {@code GeneratedJaxRsResourceBuildItem}) is routed, invokes {@code @OnMessage}
 * hooks, and proxies the EDDI response back as JSON.
 */
@QuarkusTest
@QuarkusTestResource(WireMockEddiResource.class)
class EddiAgentEndpointTest {

    @ConfigProperty(name = "wiremock.port")
    int wireMockPort;

    @Test
    void generatedEndpointExistsAndHandlesPost() {
        given()
                .contentType("text/plain")
                .queryParam("userId", "test-user")
                .body("Hello from test!")
                .when()
                .post("/test-agent")
                .then()
                .statusCode(200)
                .contentType("application/json")
                .body("conversationId", equalTo("conv-wiremock-001"))
                .body("conversationState", equalTo("READY"))
                .body("agentResponseParts", hasSize(greaterThan(0)));

        // The @OnMessage hook in SampleAgentEndpoint prefixes the message.
        new WireMock(wireMockPort).verifyThat(postRequestedFor(urlPathEqualTo("/agents/conv-wiremock-001"))
                .withRequestBody(containing("[hooked] Hello from test!")));
    }

    @Test
    void generatedEndpointUsesDefaultUserId() {
        given()
                .contentType("text/plain")
                .body("Hello anonymous!")
                .when()
                .post("/test-agent")
                .then()
                .statusCode(200)
                .body("conversationId", notNullValue());
    }
}
