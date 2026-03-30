package io.quarkiverse.eddi;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;

import io.quarkiverse.eddi.config.EddiConfig;
import io.smallrye.health.api.AsyncHealthCheck;
import io.smallrye.mutiny.Uni;

/**
 * Readiness health check that verifies connectivity to the EDDI server.
 * <p>
 * Pings the EDDI server's health endpoint. Can be disabled via
 * {@code quarkus.eddi.health.enabled=false}.
 * <p>
 * Implements {@link AsyncHealthCheck} to avoid blocking the I/O thread
 * in reactive mode.
 */
@Readiness
@ApplicationScoped
public class EddiHealthCheck implements AsyncHealthCheck {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);

    /** Reuse a single HttpClient instance instead of creating one per call. */
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .build();

    @Inject
    EddiConfig config;

    @Override
    public Uni<HealthCheckResponse> call() {
        if (!config.health().enabled()) {
            return Uni.createFrom().item(HealthCheckResponse.up("EDDI (check disabled)"));
        }

        String baseUrl = config.url();

        return Uni.createFrom().completionStage(() -> {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/q/health/ready"))
                    .timeout(REQUEST_TIMEOUT)
                    .GET()
                    .build();

            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString());
        }).map(response -> {
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return HealthCheckResponse.named("EDDI")
                        .up()
                        .withData("url", baseUrl)
                        .build();
            } else {
                return HealthCheckResponse.named("EDDI")
                        .down()
                        .withData("url", baseUrl)
                        .withData("status", response.statusCode())
                        .build();
            }
        }).onFailure().recoverWithItem(e -> HealthCheckResponse.named("EDDI")
                .down()
                .withData("error", e.getMessage())
                .build());
    }
}
