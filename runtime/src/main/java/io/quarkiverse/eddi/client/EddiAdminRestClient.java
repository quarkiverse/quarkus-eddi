package io.quarkiverse.eddi.client;

import java.util.List;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import io.quarkiverse.eddi.model.AgentDeploymentStatus;
import io.smallrye.mutiny.Uni;

/**
 * MicroProfile REST Client for EDDI v6's agent administration API.
 * <p>
 * Maps 1:1 to EDDI's {@code IRestAgentAdministration} at {@code /administration}.
 */
@RegisterRestClient(configKey = "eddi")
@RegisterProvider(EddiApiKeyFilter.class)
@Path("/administration")
public interface EddiAdminRestClient {

    @POST
    @Path("/{environment}/deploy/{agentId}")
    Uni<Response> deployAgent(
            @PathParam("environment") String environment,
            @PathParam("agentId") String agentId,
            @QueryParam("version") Integer version,
            @QueryParam("autoDeploy") @DefaultValue("true") Boolean autoDeploy,
            @QueryParam("waitForCompletion") @DefaultValue("false") Boolean waitForCompletion);

    @POST
    @Path("/{environment}/undeploy/{agentId}")
    Uni<Response> undeployAgent(
            @PathParam("environment") String environment,
            @PathParam("agentId") String agentId,
            @QueryParam("version") Integer version,
            @QueryParam("endAllActiveConversations") @DefaultValue("false") Boolean endAllActiveConversations,
            @QueryParam("undeployThisAndAllPreviousAgentVersions") @DefaultValue("false") Boolean undeployAll);

    @GET
    @Path("/{environment}/deploymentstatus/{agentId}")
    @Produces({ MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN })
    Uni<Response> getDeploymentStatus(
            @PathParam("environment") String environment,
            @PathParam("agentId") String agentId,
            @QueryParam("version") Integer version,
            @QueryParam("format") @DefaultValue("json") String format);

    @GET
    @Path("/{environment}/deploymentstatus")
    @Produces(MediaType.APPLICATION_JSON)
    Uni<List<AgentDeploymentStatus>> getDeploymentStatuses(
            @PathParam("environment") @DefaultValue("production") String environment);
}
