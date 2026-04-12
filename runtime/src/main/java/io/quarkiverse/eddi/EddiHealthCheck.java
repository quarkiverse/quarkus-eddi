package io.quarkiverse.eddi;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import io.quarkiverse.eddi.client.EddiAgentRestClient;
import io.quarkiverse.eddi.config.EddiConfig;
import io.smallrye.health.api.AsyncHealthCheck;
import io.smallrye.mutiny.Uni;

/**
 * Readiness health check that verifies connectivity to the EDDI server.
 * <p>
 * Uses the same REST client infrastructure and API key filter as all other
 * EDDI SDK calls, ensuring the health probe reflects real connectivity.
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
    EddiAgentRestClient agentClient;

    @Override
    public Uni<HealthCheckResponse> call() {
        if (!config.health().enabled()) {
            return Uni.createFrom().item(HealthCheckResponse.up("EDDI (check disabled)"));
        }

        String baseUrl = config.url();

        // Use a lightweight REST client call to verify connectivity.
        // getConversationState with a dummy ID will return 404 (not found)
        // but proves the server is reachable and the REST client is configured.
        return agentClient.getConversationState("health-check-probe")
                .map(state -> HealthCheckResponse.named("EDDI")
                        .up()
                        .withData("url", baseUrl)
                        .build())
                .onFailure(e -> {
                    // 404 is expected and means the server is healthy
                    String msg = e.getMessage();
                    return msg != null && (msg.contains("404") || msg.contains("Not Found"));
                })
                .recoverWithItem(HealthCheckResponse.named("EDDI")
                        .up()
                        .withData("url", baseUrl)
                        .build())
                .onFailure()
                .recoverWithItem(e -> HealthCheckResponse.named("EDDI")
                        .down()
                        .withData("url", baseUrl)
                        .withData("error", e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName())
                        .build());
    }
}
