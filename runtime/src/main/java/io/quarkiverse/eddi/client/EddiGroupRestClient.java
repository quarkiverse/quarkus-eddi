package io.quarkiverse.eddi.client;

import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.util.List;
import java.util.Map;

/**
 * MicroProfile REST Client for EDDI's group conversation API.
 * <p>
 * Maps to EDDI's {@code /groups/{groupId}/conversations} endpoints.
 */
@RegisterRestClient(configKey = "eddi")
@Path("/groups")
public interface EddiGroupRestClient {

    @POST
    @Path("/{groupId}/conversations")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    Uni<Response> discuss(
            @PathParam("groupId") String groupId,
            DiscussRequest request);

    @GET
    @Path("/{groupId}/conversations/{groupConversationId}")
    @Produces(MediaType.APPLICATION_JSON)
    Uni<Map<String, Object>> readGroupConversation(
            @PathParam("groupId") String groupId,
            @PathParam("groupConversationId") String groupConversationId);

    @GET
    @Path("/{groupId}/conversations")
    @Produces(MediaType.APPLICATION_JSON)
    Uni<List<Map<String, Object>>> listGroupConversations(
            @PathParam("groupId") String groupId,
            @QueryParam("index") @DefaultValue("0") Integer index,
            @QueryParam("limit") @DefaultValue("20") Integer limit);

    @DELETE
    @Path("/{groupId}/conversations/{groupConversationId}")
    Uni<Response> deleteGroupConversation(
            @PathParam("groupId") String groupId,
            @PathParam("groupConversationId") String groupConversationId);

    /**
     * Request body for starting a group discussion.
     * Matches EDDI's {@code IRestGroupConversation.DiscussRequest}.
     */
    record DiscussRequest(String question, String userId) {
    }
}
