package io.quarkiverse.eddi;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import io.quarkiverse.eddi.client.EddiHealthRestClient;
import io.quarkiverse.eddi.config.EddiConfig;
import io.smallrye.health.api.AsyncHealthCheck;
import io.smallrye.mutiny.Uni;

/**
 * Readiness health check that verifies connectivity to the EDDI server.
 * <p>
 * Probes EDDI's own readiness endpoint ({@code GET /q/health/ready}), which EDDI
 * serves without authentication. (The previous coordinator-status probe required
 * the {@code eddi-admin} role and reported DOWN for non-admin API keys.)
 * <p>
 * Reports UP when EDDI answers 200, DOWN otherwise.
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
    EddiHealthRestClient healthClient;

    @Override
    public Uni<HealthCheckResponse> call() {
        if (!config.health().enabled()) {
            return Uni.createFrom().item(HealthCheckResponse.up("EDDI (check disabled)"));
        }

        String baseUrl = config.url();

        return healthClient.ready()
                .map(response -> {
                    try (response) {
                        return HealthCheckResponse.named("EDDI")
                                .status(response.getStatus() == 200)
                                .withData("url", baseUrl)
                                .withData("status", response.getStatus())
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
