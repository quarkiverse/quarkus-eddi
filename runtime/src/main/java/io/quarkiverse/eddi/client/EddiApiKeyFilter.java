package io.quarkiverse.eddi.client;

import java.io.IOException;

import jakarta.inject.Inject;
import jakarta.ws.rs.client.ClientRequestContext;
import jakarta.ws.rs.client.ClientRequestFilter;

import io.quarkiverse.eddi.config.EddiConfig;

/**
 * JAX-RS Client filter that propagates the configured API key
 * ({@code quarkus.eddi.api-key}) as a Bearer token on all REST client requests.
 * <p>
 * Registered explicitly on each EDDI REST client interface via
 * {@code @RegisterProvider(EddiApiKeyFilter.class)}.
 * If no API key is configured, no header is added.
 */
public class EddiApiKeyFilter implements ClientRequestFilter {

    @Inject
    EddiConfig config;

    @Override
    public void filter(ClientRequestContext requestContext) throws IOException {
        config.apiKey().ifPresent(key -> {
            if (!key.isBlank()) {
                requestContext.getHeaders().putSingle("Authorization", "Bearer " + key);
            }
        });
    }
}
