package io.quarkiverse.eddi.test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.*;

import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import io.quarkiverse.eddi.EddiClient;
import io.quarkiverse.eddi.model.CoordinatorStatus;
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
    }

    @Inject
    EddiClient eddi;

    @ConfigProperty(name = "quarkus.eddi.url")
    String eddiUrl;

    @Test
    void devServicesStartedEddiContainer() {
        assertFalse(eddiUrl.contains(":7070") && eddiUrl.contains("localhost"),
                "quarkus.eddi.url should point at the Dev Services container, was " + eddiUrl);
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
}
