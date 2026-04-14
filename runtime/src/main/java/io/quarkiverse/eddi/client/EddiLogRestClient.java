package io.quarkiverse.eddi.client;

import java.util.List;
import java.util.Map;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

import org.eclipse.microprofile.rest.client.annotation.RegisterProvider;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import io.quarkiverse.eddi.model.LogEntry;
import io.smallrye.mutiny.Uni;

/**
 * MicroProfile REST Client for EDDI v6's log administration API.
 * <p>
 * Maps to EDDI's {@code IRestLogAdmin} at {@code /administration/logs}.
 * <p>
 * Note: The {@code /stream} SSE endpoint is not exposed here — use
 * Vert.x EventBus or a dedicated SSE client for live log tailing.
 */
@RegisterRestClient(configKey = "eddi")
@RegisterProvider(EddiApiKeyFilter.class)
@Path("/administration/logs")
public interface EddiLogRestClient {

    /**
     * Get recent logs from the in-memory ring buffer.
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    Uni<List<LogEntry>> getRecentLogs(
            @QueryParam("agentId") String agentId,
            @QueryParam("conversationId") String conversationId,
            @QueryParam("level") @DefaultValue("INFO") String level,
            @QueryParam("limit") @DefaultValue("100") int limit);

    /**
     * Get historical logs from persistent storage.
     */
    @GET
    @Path("/history")
    @Produces(MediaType.APPLICATION_JSON)
    Uni<List<Map<String, Object>>> getHistoryLogs(
            @QueryParam("environment") String environment,
            @QueryParam("agentId") String agentId,
            @QueryParam("agentVersion") Integer agentVersion,
            @QueryParam("conversationId") String conversationId,
            @QueryParam("userId") String userId,
            @QueryParam("instanceId") String instanceId,
            @QueryParam("skip") @DefaultValue("0") Integer skip,
            @QueryParam("limit") @DefaultValue("100") Integer limit);

    /**
     * Get the current EDDI instance identifier (for cluster tracking).
     */
    @GET
    @Path("/instance-id")
    @Produces(MediaType.APPLICATION_JSON)
    Uni<Map<String, String>> getInstanceId();
}
