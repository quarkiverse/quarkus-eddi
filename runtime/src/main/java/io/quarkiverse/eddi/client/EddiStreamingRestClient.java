package io.quarkiverse.eddi.client;

import java.util.List;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.sse.InboundSseEvent;

import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import io.quarkiverse.eddi.model.InputData;
import io.smallrye.mutiny.Multi;

/**
 * MicroProfile REST Client for EDDI v6's SSE streaming conversation API.
 * <p>
 * Maps to EDDI's {@code IRestAgentEngineStreaming} at
 * {@code POST /agents/{conversationId}/stream}.
 * <p>
 * Returns a {@link Multi} of {@link InboundSseEvent} objects that preserve
 * the SSE event name ({@code task_start}, {@code token}, {@code done},
 * {@code error}, etc.) alongside the payload data.
 */
@RegisterRestClient(configKey = "eddi")
@RegisterProvider(EddiApiKeyFilter.class)
@Path("/agents")
public interface EddiStreamingRestClient {

    /**
     * Send a message and receive a streaming SSE response with workflow events
     * and LLM tokens.
     *
     * @param conversationId the conversation to send into
     * @param returnDetailed whether to include detailed step data
     * @param returnCurrentStepOnly only return the latest step
     * @param returningFields filter fields to return
     * @param inputData the user message with optional context
     * @return a Multi of SSE events preserving event name and data
     */
    @POST
    @Path("/{conversationId}/stream")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.SERVER_SENT_EVENTS)
    Multi<InboundSseEvent> sayStreaming(
            @PathParam("conversationId") String conversationId,
            @QueryParam("returnDetailed") @DefaultValue("false") Boolean returnDetailed,
            @QueryParam("returnCurrentStepOnly") @DefaultValue("true") Boolean returnCurrentStepOnly,
            @QueryParam("returningFields") List<String> returningFields,
            InputData inputData);
}
