package io.quarkiverse.eddi.client;

import java.util.List;
import java.util.Map;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import org.jboss.resteasy.reactive.RestStreamElementType;
import org.jboss.resteasy.reactive.client.SseEvent;

import com.fasterxml.jackson.annotation.JsonInclude;

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
    @RestStreamElementType(MediaType.TEXT_PLAIN)
    Multi<SseEvent<String>> discussStreaming(
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

    // --- Follow-up / continue / close (lifecycle: discuss → COMPLETED → [followup|continue]* → close) ---

    /**
     * Ask one member of a COMPLETED group conversation a follow-up question.
     * Returns 200 with the updated group conversation.
     */
    @POST
    @Path("/{groupId}/conversations/{groupConversationId}/followup")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    Uni<Response> followUpWithMember(
            @PathParam("groupId") String groupId,
            @PathParam("groupConversationId") String groupConversationId,
            FollowUpRequest request);

    /**
     * Re-run all discussion phases of a COMPLETED group conversation with a new
     * question (a new round). Attachments are rejected with 400 on a continuation.
     */
    @POST
    @Path("/{groupId}/conversations/{groupConversationId}/continue")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    Uni<Response> continueDiscussion(
            @PathParam("groupId") String groupId,
            @PathParam("groupConversationId") String groupConversationId,
            DiscussRequest request);

    /**
     * Continue a group discussion and stream progress via SSE ({@code round_start}
     * followed by the same events as {@link #discussStreaming}).
     */
    @POST
    @Path("/{groupId}/conversations/{groupConversationId}/continue/stream")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @RestStreamElementType(MediaType.TEXT_PLAIN)
    Multi<SseEvent<String>> continueDiscussionStreaming(
            @PathParam("groupId") String groupId,
            @PathParam("groupConversationId") String groupConversationId,
            DiscussRequest request);

    /**
     * Permanently close a group conversation (terminal CLOSED state); ends all
     * member conversations.
     */
    @POST
    @Path("/{groupId}/conversations/{groupConversationId}/close")
    @Produces(MediaType.APPLICATION_JSON)
    Uni<Response> closeGroupConversation(
            @PathParam("groupId") String groupId,
            @PathParam("groupConversationId") String groupConversationId);

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
    @RestStreamElementType(MediaType.TEXT_PLAIN)
    Multi<SseEvent<String>> approveGroupPhaseStreaming(
            @PathParam("groupId") String groupId,
            @PathParam("groupConversationId") String groupConversationId,
            GroupApprovalRequest request);

    /**
     * Submit a HUMAN group member's turn and resume an
     * {@code AWAITING_HUMAN_INPUT} discussion from the next speaker. Only the
     * pending member's own principal (or an admin) may submit.
     */
    @POST
    @Path("/{groupId}/conversations/{groupConversationId}/human-input")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    Uni<Response> submitHumanInput(
            @PathParam("groupId") String groupId,
            @PathParam("groupConversationId") String groupConversationId,
            HumanInputRequest request);

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
     * Request body for starting (or continuing) a group discussion. Optional
     * {@code attachments} are shared with every member agent; they are only
     * accepted when a discussion starts.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record DiscussRequest(String question, String userId, List<AttachmentRef> attachments) {
        public DiscussRequest(String question, String userId) {
            this(question, userId, null);
        }
    }

    /**
     * An attachment on a group discussion: either inline base64 {@code data}
     * (with {@code mimeType}) or a hosted {@code url}.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record AttachmentRef(String mimeType, String data, String url, String fileName) {

        public static AttachmentRef inline(String mimeType, String base64Data, String fileName) {
            return new AttachmentRef(mimeType, base64Data, null, fileName);
        }

        public static AttachmentRef url(String url) {
            return new AttachmentRef(null, null, url, null);
        }
    }

    /**
     * Request body for a follow-up question. {@code targetAgentId} accepts an agent
     * id or a member display name.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record FollowUpRequest(String question, String targetAgentId, String userId) {
    }

    /**
     * Request body for a HUMAN member's turn: which member is speaking and what they said.
     */
    record HumanInputRequest(String memberId, String content) {
    }
}
