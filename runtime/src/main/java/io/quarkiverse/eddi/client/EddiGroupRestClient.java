package io.quarkiverse.eddi.client;

import java.util.List;
import java.util.Map;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import io.smallrye.mutiny.Uni;

/**
 * MicroProfile REST Client for EDDI v6's group conversation API.
 * <p>
 * Maps 1:1 to EDDI's {@code IRestGroupConversation} at
 * {@code /groups/{groupId}/conversations}.
 */
@RegisterRestClient(configKey = "eddi")
@RegisterProvider(EddiApiKeyFilter.class)
@Path("/groups/{groupId}/conversations")
public interface EddiGroupRestClient {

    /**
     * Start a group discussion with a question.
     *
     * @param groupId the group identifier
     * @param request the discussion request (question + optional userId)
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    Uni<Response> discuss(
            @PathParam("groupId") String groupId,
            DiscussRequest request);

    /**
     * Read a specific group conversation transcript.
     */
    @GET
    @Path("/{groupConversationId}")
    @Produces(MediaType.APPLICATION_JSON)
    Uni<Map<String, Object>> readGroupConversation(
            @PathParam("groupId") String groupId,
            @PathParam("groupConversationId") String groupConversationId);

    /**
     * Delete a group conversation and its member conversations.
     */
    @DELETE
    @Path("/{groupConversationId}")
    Uni<Response> deleteGroupConversation(
            @PathParam("groupId") String groupId,
            @PathParam("groupConversationId") String groupConversationId);

    /**
     * List group conversations with pagination.
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    Uni<List<Map<String, Object>>> listGroupConversations(
            @PathParam("groupId") String groupId,
            @QueryParam("index") @DefaultValue("0") Integer index,
            @QueryParam("limit") @DefaultValue("20") Integer limit);

    /**
     * Request body for starting a group discussion.
     */
    record DiscussRequest(String question, String userId) {
    }
}
