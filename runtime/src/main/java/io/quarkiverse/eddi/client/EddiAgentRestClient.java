package io.quarkiverse.eddi.client;

import java.util.List;
import java.util.Map;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import io.quarkiverse.eddi.model.Context;
import io.quarkiverse.eddi.model.ConversationState;
import io.quarkiverse.eddi.model.HitlDecision;
import io.quarkiverse.eddi.model.InputData;
import io.quarkiverse.eddi.model.PendingApprovalSummary;
import io.smallrye.mutiny.Uni;

/**
 * MicroProfile REST Client interface for EDDI v6's core conversation API.
 * <p>
 * Maps 1:1 to EDDI's {@code IRestAgentEngine} interface at {@code /agents}.
 * <p>
 * Design note: The server uses {@code AsyncResponse} for say/rerun operations,
 * but the REST client transparently handles this — the returned {@code Uni}
 * completes when the server responds.
 */
@RegisterRestClient(configKey = "eddi")
@RegisterProvider(EddiApiKeyFilter.class)
@Path("/agents")
public interface EddiAgentRestClient {

    // --- Start conversation ---

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
            Map<String, Context> context);

    // --- End conversation ---

    @POST
    @Path("/{conversationId}/endConversation")
    Uni<Response> endConversation(
            @PathParam("conversationId") String conversationId);

    // --- Read / Log ---

    @GET
    @Path("/{conversationId}/log")
    @Produces(MediaType.APPLICATION_JSON)
    Uni<Response> readConversationLog(
            @PathParam("conversationId") String conversationId,
            @QueryParam("outputType") @DefaultValue("json") String outputType,
            @QueryParam("logSize") @DefaultValue("-1") Integer logSize);

    @GET
    @Path("/{conversationId}")
    @Produces(MediaType.APPLICATION_JSON)
    Uni<Map<String, Object>> readConversation(
            @PathParam("conversationId") String conversationId,
            @QueryParam("returnDetailed") @DefaultValue("false") Boolean returnDetailed,
            @QueryParam("returnCurrentStepOnly") @DefaultValue("true") Boolean returnCurrentStepOnly,
            @QueryParam("returningFields") List<String> returningFields);

    @GET
    @Path("/{conversationId}/status")
    @Produces(MediaType.APPLICATION_JSON)
    Uni<ConversationState> getConversationState(
            @PathParam("conversationId") String conversationId);

    // --- Talk (say) ---

    @POST
    @Path("/{conversationId}/rerun")
    @Produces(MediaType.APPLICATION_JSON)
    Uni<Map<String, Object>> rerunLastConversationStep(
            @PathParam("conversationId") String conversationId,
            @QueryParam("language") String language,
            @QueryParam("returnDetailed") @DefaultValue("false") Boolean returnDetailed,
            @QueryParam("returnCurrentStepOnly") @DefaultValue("true") Boolean returnCurrentStepOnly,
            @QueryParam("returningFields") List<String> returningFields);

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
    Uni<Map<String, Object>> sayWithinContext(
            @PathParam("conversationId") String conversationId,
            @QueryParam("returnDetailed") @DefaultValue("false") Boolean returnDetailed,
            @QueryParam("returnCurrentStepOnly") @DefaultValue("true") Boolean returnCurrentStepOnly,
            @QueryParam("returningFields") List<String> returningFields,
            InputData inputData);

    // --- Undo / Redo ---

    @GET
    @Path("/{conversationId}/undo")
    @Produces(MediaType.TEXT_PLAIN)
    Uni<Boolean> isUndoAvailable(
            @PathParam("conversationId") String conversationId);

    @POST
    @Path("/{conversationId}/undo")
    Uni<Response> undo(@PathParam("conversationId") String conversationId);

    @GET
    @Path("/{conversationId}/redo")
    @Produces(MediaType.TEXT_PLAIN)
    Uni<Boolean> isRedoAvailable(
            @PathParam("conversationId") String conversationId);

    @POST
    @Path("/{conversationId}/redo")
    Uni<Response> redo(@PathParam("conversationId") String conversationId);

    // --- Cancel ---

    @POST
    @Path("/{conversationId}/cancel")
    Uni<Response> cancelConversation(@PathParam("conversationId") String conversationId);

    // --- HITL (Human-in-the-Loop) ---

    /**
     * Submit a human decision (APPROVED/REJECTED) to resume a conversation that is
     * in {@code AWAITING_HUMAN} state. Returns 409 if the conversation is not
     * awaiting approval.
     */
    @POST
    @Path("/{conversationId}/resume")
    @Consumes(MediaType.APPLICATION_JSON)
    Uni<Response> resumeConversation(
            @PathParam("conversationId") String conversationId,
            HitlDecision decision);

    /**
     * Get the HITL approval status of a paused conversation. Use
     * {@code detail=full} for the complete memory snapshot, {@code detail=summary}
     * (the default) for the pause coordinates only.
     */
    @GET
    @Path("/{conversationId}/approval-status")
    @Produces(MediaType.APPLICATION_JSON)
    Uni<Response> getApprovalStatus(
            @PathParam("conversationId") String conversationId,
            @QueryParam("detail") @DefaultValue("summary") String detail);

    /**
     * List conversations currently awaiting human approval (bounded by
     * {@code limit}, server max 1000).
     */
    @GET
    @Path("/pending-approvals")
    @Produces(MediaType.APPLICATION_JSON)
    Uni<List<PendingApprovalSummary>> listPendingApprovals(
            @QueryParam("limit") @DefaultValue("200") Integer limit);
}
