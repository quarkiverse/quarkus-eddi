package io.quarkiverse.eddi.client;

import java.util.List;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.sse.InboundSseEvent;

import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import io.quarkiverse.eddi.model.CoordinatorStatus;
import io.quarkiverse.eddi.model.DeadLetterEntry;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;

/**
 * MicroProfile REST Client for EDDI v6's coordinator administration API.
 * <p>
 * Maps to EDDI's {@code IRestCoordinatorAdmin} at
 * {@code /administration/coordinator}.
 */
@RegisterRestClient(configKey = "eddi")
@RegisterProvider(EddiApiKeyFilter.class)
@Path("/administration/coordinator")
public interface EddiCoordinatorRestClient {

    /**
     * Get coordinator status (type, connection state, queue depths, stats).
     */
    @GET
    @Path("/status")
    @Produces(MediaType.APPLICATION_JSON)
    Uni<CoordinatorStatus> getStatus();

    /**
     * List all dead-letter entries.
     */
    @GET
    @Path("/dead-letters")
    @Produces(MediaType.APPLICATION_JSON)
    Uni<List<DeadLetterEntry>> getDeadLetters();

    /**
     * Replay a dead-letter entry (re-inject into the processing pipeline).
     */
    @POST
    @Path("/dead-letters/{entryId}/replay")
    Uni<Void> replayDeadLetter(
            @PathParam("entryId") String entryId);

    /**
     * Discard a single dead-letter entry.
     */
    @DELETE
    @Path("/dead-letters/{entryId}")
    Uni<Void> discardDeadLetter(
            @PathParam("entryId") String entryId);

    /**
     * Purge all dead-letter entries. Returns count of purged entries.
     */
    @DELETE
    @Path("/dead-letters")
    @Produces(MediaType.APPLICATION_JSON)
    Uni<Integer> purgeDeadLetters();

    /**
     * Live-tail coordinator events via SSE (e.g. {@code task_completed},
     * {@code task_failed}, {@code task_dead_lettered}).
     */
    @GET
    @Path("/stream")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    Multi<InboundSseEvent> streamEvents();
}
