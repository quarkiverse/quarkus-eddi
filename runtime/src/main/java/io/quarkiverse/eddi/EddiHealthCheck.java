package io.quarkiverse.eddi;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import io.quarkiverse.eddi.client.EddiCoordinatorRestClient;
import io.quarkiverse.eddi.config.EddiConfig;
import io.smallrye.health.api.AsyncHealthCheck;
import io.smallrye.mutiny.Uni;

/**
 * Readiness health check that verifies connectivity to the EDDI server.
 * <p>
 * Uses the coordinator status API ({@code GET /administration/coordinator/status})
 * to verify that the EDDI server is reachable and operational. This is a lightweight,
 * purpose-built admin endpoint that returns the coordinator type, connection state,
 * and queue statistics — avoiding the 404 log pollution of the previous dummy-probe approach.
 * <p>
 * Reports UP when the coordinator is connected, DOWN otherwise.
 * Can be disabled via {@code quarkus.eddi.health.enabled=false}.
 * <p>
 * Implements {@link AsyncHealthCheck} to avoid blocking the I/O thread
 * in reactive mode.
 */
@Readiness
@ApplicationScoped
public class EddiHealthCheck implements AsyncHealthCheck {

    @Inject
    EddiConfig config;

    @Inject
    @RestClient
    EddiCoordinatorRestClient coordinatorClient;

    @Override
    public Uni<HealthCheckResponse> call() {
        if (!config.health().enabled()) {
            return Uni.createFrom().item(HealthCheckResponse.up("EDDI (check disabled)"));
        }

        String baseUrl = config.url();

        return coordinatorClient.getStatus()
                .map(status -> {
                    if (status != null && status.connected()) {
                        return HealthCheckResponse.named("EDDI")
                                .up()
                                .withData("url", baseUrl)
                                .withData("coordinator", status.coordinatorType())
                                .withData("activeConversations", status.activeConversations())
                                .build();
                    } else {
                        return HealthCheckResponse.named("EDDI")
                                .down()
                                .withData("url", baseUrl)
                                .withData("reason", status != null
                                        ? "coordinator disconnected: " + status.connectionStatus()
                                        : "null status response")
                                .build();
                    }
                })
                .onFailure()
                .recoverWithItem(e -> HealthCheckResponse.named("EDDI")
                        .down()
                        .withData("url", baseUrl)
                        .withData("error", e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName())
                        .build());
    }
}
