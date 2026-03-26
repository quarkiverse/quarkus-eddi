package io.quarkiverse.eddi.client;

import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.util.List;
import java.util.Map;

/**
 * MicroProfile REST Client interface for EDDI's core conversation API.
 * <p>
 * Maps to EDDI's {@code /agents} endpoints.
 */
@RegisterRestClient(configKey = "eddi")
@Path("/agents")
public interface EddiAgentRestClient {

    @POST
    @Path("/{agentId}/start")
    Uni<Response> startConversation(
            @PathParam("agentId") String agentId,
            @QueryParam("environment") @DefaultValue("production") String environment,
            @QueryParam("userId") String userId);

    @POST
    @Path("/{agentId}/start")
    @Consumes(MediaType.APPLICATION_JSON)
    Uni<Response> startConversationWithContext(
            @PathParam("agentId") String agentId,
            @QueryParam("environment") @DefaultValue("production") String environment,
            @QueryParam("userId") String userId,
            Map<String, Object> context);

    @POST
    @Path("/{conversationId}")
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces(MediaType.APPLICATION_JSON)
    Uni<Map<String, Object>> say(
            @PathParam("conversationId") String conversationId,
            @QueryParam("returnDetailed") @DefaultValue("false") Boolean returnDetailed,
            @QueryParam("returnCurrentStepOnly") @DefaultValue("true") Boolean returnCurrentStepOnly,
            @QueryParam("returningFields") List<String> returningFields,
            String message);

    @POST
    @Path("/{conversationId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    Uni<Map<String, Object>> sayWithContext(
            @PathParam("conversationId") String conversationId,
            @QueryParam("returnDetailed") @DefaultValue("false") Boolean returnDetailed,
            @QueryParam("returnCurrentStepOnly") @DefaultValue("true") Boolean returnCurrentStepOnly,
            @QueryParam("returningFields") List<String> returningFields,
            Map<String, Object> inputData);

    @GET
    @Path("/{conversationId}")
    @Produces(MediaType.APPLICATION_JSON)
    Uni<Map<String, Object>> readConversation(
            @PathParam("conversationId") String conversationId,
            @QueryParam("returnDetailed") @DefaultValue("false") Boolean returnDetailed,
            @QueryParam("returnCurrentStepOnly") @DefaultValue("true") Boolean returnCurrentStepOnly,
            @QueryParam("returningFields") List<String> returningFields);

    @GET
    @Path("/{conversationId}/log")
    @Produces(MediaType.APPLICATION_JSON)
    Uni<Response> readConversationLog(
            @PathParam("conversationId") String conversationId,
            @QueryParam("outputType") @DefaultValue("json") String outputType,
            @QueryParam("logSize") @DefaultValue("-1") Integer logSize);

    @GET
    @Path("/{conversationId}/status")
    @Produces(MediaType.APPLICATION_JSON)
    Uni<String> getConversationState(
            @PathParam("conversationId") String conversationId);

    @POST
    @Path("/{conversationId}/endConversation")
    Uni<Response> endConversation(
            @PathParam("conversationId") String conversationId);

    @POST
    @Path("/{conversationId}/undo")
    Uni<Response> undo(@PathParam("conversationId") String conversationId);

    @POST
    @Path("/{conversationId}/redo")
    Uni<Response> redo(@PathParam("conversationId") String conversationId);
}
