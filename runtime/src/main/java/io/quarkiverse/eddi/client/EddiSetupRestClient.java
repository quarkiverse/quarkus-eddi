package io.quarkiverse.eddi.client;

import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.util.Map;

/**
 * MicroProfile REST Client for EDDI's one-command agent setup API.
 * <p>
 * Maps to EDDI's {@code /administration/agents} endpoints.
 */
@RegisterRestClient(configKey = "eddi")
@Path("/administration/agents")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface EddiSetupRestClient {

    @POST
    @Path("/setup")
    Uni<Response> setupAgent(Map<String, Object> request);

    @POST
    @Path("/setup-api")
    Uni<Response> createApiAgent(Map<String, Object> request);
}
