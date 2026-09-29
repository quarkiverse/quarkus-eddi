package io.quarkiverse.eddi.client;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import io.smallrye.mutiny.Uni;

/**
 * MicroProfile REST Client for EDDI's own SmallRye Health endpoint.
 * <p>
 * {@code /q/health/ready} is publicly reachable on EDDI even when OIDC is enabled,
 * so it works regardless of the roles attached to {@code quarkus.eddi.api-key}.
 * Returns 200 when EDDI is ready and 503 when it is not.
 */
@RegisterRestClient(configKey = "eddi")
@Path("/q/health")
public interface EddiHealthRestClient {

    @GET
    @Path("/ready")
    @Produces(MediaType.APPLICATION_JSON)
    Uni<Response> ready();
}
