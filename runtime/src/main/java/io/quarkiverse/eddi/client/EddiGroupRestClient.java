package io.quarkiverse.eddi.client;

import java.util.List;
import java.util.Map;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.sse.InboundSseEvent;

import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import io.quarkiverse.eddi.model.GroupApprovalRequest;
import io.quarkiverse.eddi.model.PendingApprovalSummary;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;

/**
 * MicroProfile REST Client for EDDI v6's group conversation API.
 * <p>
 * Maps 1:1 to EDDI's {@code IRestGroupConversation}. The class path is the
 * {@code /groups} root (not {@code /groups/{groupId}/conversations}) so the
 * cross-group HITL inbox {@code GET /groups/pending-approvals} can live alongside
 * the per-group routes; every per-group method carries the
 * {@code /{groupId}/conversations} prefix, matching the server exactly.
 */
@RegisterRestClient(configKey = "eddi")
@RegisterProvider(EddiApiKeyFilter.class)
@Path("/groups")
public interface EddiGroupRestClient {

    /**
     * Start a group discussion with a question.
     *
     * @param groupId the group identifier
     * @param request the discussion request (question + optional userId)
     */
    @POST
    @Path("/{groupId}/conversations")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    Uni<Response> discuss(
            @PathParam("groupId") String groupId,
            DiscussRequest request);

    /**
     * Start a group discussion and stream progress events via SSE
     * ({@code group_start}, {@code phase_start}, {@code speaker_start},
     * {@code speaker_complete}, {@code phase_complete}, {@code synthesis_start},
     * {@code group_complete}, {@code group_error}).
     */
    @POST
    @Path("/{groupId}/conversations/stream")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.SERVER_SENT_EVENTS)
    Multi<InboundSseEvent> discussStreaming(
            @PathParam("groupId") String groupId,
            DiscussRequest request);

    /**
     * Read a specific group conversation transcript.
     */
    @GET
    @Path("/{groupId}/conversations/{groupConversationId}")
    @Produces(MediaType.APPLICATION_JSON)
    Uni<Map<String, Object>> readGroupConversation(
            @PathParam("groupId") String groupId,
            @PathParam("groupConversationId") String groupConversationId);

    /**
     * Delete a group conversation and its member conversations.
     */
    @DELETE
    @Path("/{groupId}/conversations/{groupConversationId}")
    Uni<Response> deleteGroupConversation(
            @PathParam("groupId") String groupId,
            @PathParam("groupConversationId") String groupConversationId);

    /**
     * List group conversations with pagination.
     */
    @GET
    @Path("/{groupId}/conversations")
    @Produces(MediaType.APPLICATION_JSON)
    Uni<List<Map<String, Object>>> listGroupConversations(
            @PathParam("groupId") String groupId,
            @QueryParam("index") @DefaultValue("0") Integer index,
            @QueryParam("limit") @DefaultValue("20") Integer limit);

    // --- Cancel ---

    /**
     * Cancel an in-progress group discussion.
     */
    @POST
    @Path("/{groupId}/conversations/{groupConversationId}/cancel")
    @Consumes(MediaType.APPLICATION_JSON)
    Uni<Response> cancelDiscussion(
            @PathParam("groupId") String groupId,
            @PathParam("groupConversationId") String groupConversationId);

    // --- HITL (Human-in-the-Loop) ---

    /**
     * Approve/resume a paused group discussion after human approval.
     */
    @POST
    @Path("/{groupId}/conversations/{groupConversationId}/approve")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    Uni<Response> approveGroupPhase(
            @PathParam("groupId") String groupId,
            @PathParam("groupConversationId") String groupConversationId,
            GroupApprovalRequest request);

    /**
     * Approve/resume a paused group discussion and stream the resumed progress via
     * SSE.
     */
    @POST
    @Path("/{groupId}/conversations/{groupConversationId}/approve/stream")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.SERVER_SENT_EVENTS)
    Multi<InboundSseEvent> approveGroupPhaseStreaming(
            @PathParam("groupId") String groupId,
            @PathParam("groupConversationId") String groupConversationId,
            GroupApprovalRequest request);

    /**
     * Get the approval status of a group conversation. Use {@code detail=full} for
     * the complete conversation, {@code detail=summary} (the default) for the pause
     * coordinates only.
     */
    @GET
    @Path("/{groupId}/conversations/{groupConversationId}/approval-status")
    @Produces(MediaType.APPLICATION_JSON)
    Uni<Response> getGroupApprovalStatus(
            @PathParam("groupId") String groupId,
            @PathParam("groupConversationId") String groupConversationId,
            @QueryParam("detail") @DefaultValue("summary") String detail);

    /**
     * List this group's conversations currently awaiting human approval.
     */
    @GET
    @Path("/{groupId}/conversations/pending-approvals")
    @Produces(MediaType.APPLICATION_JSON)
    Uni<List<PendingApprovalSummary>> listGroupPendingApprovals(
            @PathParam("groupId") String groupId,
            @QueryParam("limit") @DefaultValue("100") Integer limit);

    /**
     * Cross-group HITL inbox — all group conversations awaiting human approval
     * across every group, as bounded summaries (no transcripts).
     */
    @GET
    @Path("/pending-approvals")
    @Produces(MediaType.APPLICATION_JSON)
    Uni<List<PendingApprovalSummary>> listAllGroupPendingApprovals(
            @QueryParam("limit") @DefaultValue("100") Integer limit);

    /**
     * Request body for starting a group discussion.
     */
    record DiscussRequest(String question, String userId) {
    }
}
