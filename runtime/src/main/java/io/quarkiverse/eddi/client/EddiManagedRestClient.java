package io.quarkiverse.eddi.client;

import java.util.List;
import java.util.Map;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import io.quarkiverse.eddi.model.InputData;
import io.smallrye.mutiny.Uni;

/**
 * MicroProfile REST Client for EDDI v6's managed agent API.
 * <p>
 * Maps 1:1 to EDDI's {@code IRestAgentManagement} at {@code /agents/managed}.
 * <p>
 * Managed agents automatically resolve the active conversation for a given
 * intent/userId pair — no conversation ID management needed.
 */
@RegisterRestClient(configKey = "eddi")
@RegisterProvider(EddiApiKeyFilter.class)
@Path("/agents/managed")
public interface EddiManagedRestClient {

    @GET
    @Path("/{intent}/{userId}")
    @Produces(MediaType.APPLICATION_JSON)
    Uni<Map<String, Object>> loadConversationMemory(
            @PathParam("intent") String intent,
            @PathParam("userId") String userId,
            @QueryParam("language") String language,
            @QueryParam("returnDetailed") @DefaultValue("false") Boolean returnDetailed,
            @QueryParam("returnCurrentStepOnly") @DefaultValue("true") Boolean returnCurrentStepOnly,
            @QueryParam("returningFields") List<String> returningFields);

    @POST
    @Path("/{intent}/{userId}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    Uni<Map<String, Object>> sayWithinContext(
            @PathParam("intent") String intent,
            @PathParam("userId") String userId,
            @QueryParam("returnDetailed") @DefaultValue("false") Boolean returnDetailed,
            @QueryParam("returnCurrentStepOnly") @DefaultValue("true") Boolean returnCurrentStepOnly,
            @QueryParam("returningFields") List<String> returningFields,
            InputData inputData);

    @POST
    @Path("/{intent}/{userId}/endConversation")
    Uni<Response> endCurrentConversation(
            @PathParam("intent") String intent,
            @PathParam("userId") String userId);

    @GET
    @Path("/{intent}/{userId}/undo")
    @Produces(MediaType.TEXT_PLAIN)
    Uni<Boolean> isUndoAvailable(
            @PathParam("intent") String intent,
            @PathParam("userId") String userId);

    @POST
    @Path("/{intent}/{userId}/undo")
    Uni<Response> undo(
            @PathParam("intent") String intent,
            @PathParam("userId") String userId);

    @GET
    @Path("/{intent}/{userId}/redo")
    @Produces(MediaType.TEXT_PLAIN)
    Uni<Boolean> isRedoAvailable(
            @PathParam("intent") String intent,
            @PathParam("userId") String userId);

    @POST
    @Path("/{intent}/{userId}/redo")
    Uni<Response> redo(
            @PathParam("intent") String intent,
            @PathParam("userId") String userId);
}
