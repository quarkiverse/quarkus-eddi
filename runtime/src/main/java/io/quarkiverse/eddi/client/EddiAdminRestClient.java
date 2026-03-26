package io.quarkiverse.eddi.client;

import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.util.List;
import java.util.Map;

/**
 * MicroProfile REST Client for EDDI's administration/deployment API.
 * <p>
 * Maps to EDDI's {@code /administration} endpoints.
 * All parameters match the server-side {@code IRestAgentAdministration} interface.
 */
@RegisterRestClient(configKey = "eddi")
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
            @QueryParam("undeployThisAndAllPreviousAgentVersions") @DefaultValue("false") Boolean undeployAllPrevious);

    @GET
    @Path("/{environment}/deploymentstatus/{agentId}")
    @Produces({MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN})
    Uni<Response> getDeploymentStatus(
            @PathParam("environment") String environment,
            @PathParam("agentId") String agentId,
            @QueryParam("version") Integer version,
            @QueryParam("format") @DefaultValue("json") String format);

    @GET
    @Path("/{environment}/deploymentstatus")
    @Produces(MediaType.APPLICATION_JSON)
    Uni<List<Map<String, Object>>> getDeploymentStatuses(
            @PathParam("environment") @DefaultValue("production") String environment);
}
