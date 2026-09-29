package io.quarkiverse.eddi.client;

import java.util.List;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import org.jboss.resteasy.reactive.RestStreamElementType;
import org.jboss.resteasy.reactive.client.SseEvent;

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
     * Live coordinator status via SSE. EDDI 6.4 sends {@code status} events only: one on
     * connect, then one every 2 seconds, each carrying the {@link CoordinatorStatus} JSON
     * (the same payload as {@link #getStatus()}). Individual task events are not streamed,
     * although EDDI's OpenAPI description mentions them. The stream stays open until cancelled.
     */
    @GET
    @Path("/stream")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    @RestStreamElementType(MediaType.TEXT_PLAIN)
    Multi<SseEvent<String>> streamEvents();
}
