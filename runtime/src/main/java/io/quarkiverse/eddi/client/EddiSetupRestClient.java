package io.quarkiverse.eddi.client;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import io.quarkiverse.eddi.model.CreateApiAgentRequest;
import io.quarkiverse.eddi.model.SetupAgentRequest;
import io.quarkiverse.eddi.model.SetupResult;
import io.smallrye.mutiny.Uni;

/**
 * MicroProfile REST Client for EDDI v6's agent setup API.
 * <p>
 * Maps 1:1 to EDDI's {@code IRestAgentSetup} at {@code /administration/agents}.
 */
@RegisterRestClient(configKey = "eddi")
@RegisterProvider(EddiApiKeyFilter.class)
@Path("/administration/agents")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface EddiSetupRestClient {

    /**
     * Setup a standard agent with LLM configuration, parser, behavior rules, etc.
     */
    @POST
    @Path("/setup")
    Uni<SetupResult> setupAgent(SetupAgentRequest request);

    /**
     * Create an API agent from an OpenAPI specification.
     */
    @POST
    @Path("/setup-api")
    Uni<SetupResult> createApiAgent(CreateApiAgentRequest request);
}
