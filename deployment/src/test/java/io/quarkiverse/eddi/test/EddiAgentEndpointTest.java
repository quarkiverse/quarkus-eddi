package io.quarkiverse.eddi.test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Integration test for {@code @EddiAgent} build-time endpoint generation.
 * <p>
 * <strong>Status: Disabled — Quarkus limitation.</strong>
 * The bytecode IS generated correctly (confirmed by build logs:
 * {@code "Generated @EddiAgent resource: SampleAgentEndpoint_EddiResource"})
 * and RESTEasy Reactive sees the class, but {@code ClassTransformingBuildStep}
 * reports "cannot transform — containing application archive could not be found".
 * <p>
 * This is a known limitation when using {@code GeneratedBeanBuildItem} for
 * JAX-RS resources. The generated class lives outside any application archive,
 * so RESTEasy can't fully process it. The fix requires producing the endpoint
 * as a Vert.x route or using {@code quarkus-rest-spi-deployment}'s
 * {@code AdditionalResourceClassBuildItem} with a synthetic ClassInfo.
 * <p>
 * <strong>What this test will verify once fixed:</strong>
 * <ul>
 * <li>Gizmo bytecode generation produces a valid JAX-RS resource</li>
 * <li>POST to /test-agent routes to the generated endpoint</li>
 * <li>{@code @OnMessage} hooks are invoked</li>
 * <li>EDDI response is correctly proxied back</li>
 * </ul>
 *
 * @see <a href="https://github.com/quarkusio/quarkus/issues/36841">quarkus#36841</a>
 */
@QuarkusTest
@QuarkusTestResource(WireMockEddiResource.class)
@Disabled("GeneratedBeanBuildItem classes not fully supported as JAX-RS resources by RESTEasy Reactive — tracked as post-6.0.0 work")
class EddiAgentEndpointTest {

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
