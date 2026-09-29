package io.quarkiverse.eddi.test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;

/**
 * The readiness check reports DOWN when EDDI is unreachable.
 */
@QuarkusTest
@TestProfile(EddiHealthDownTest.UnreachableEddi.class)
class EddiHealthDownTest {

    public static class UnreachableEddi implements QuarkusTestProfile {
        @Override
        public Map<String, String> getConfigOverrides() {
            // Nothing listens on port 1
            return Map.of(
                    "quarkus.eddi.url", "http://localhost:1",
                    "quarkus.rest-client.eddi.url", "http://localhost:1");
        }

        @Override
        public boolean disableGlobalTestResources() {
            return true;
        }
    }

    @Test
    void readinessIsDownWhenEddiIsUnreachable() {
        given()
                .when().get("/q/health/ready")
                .then()
                .statusCode(503)
                .body("checks.find { it.name == 'EDDI' }.status", equalTo("DOWN"));
    }
}
