package io.quarkiverse.eddi.model;

import java.util.Map;

/**
 * Status of the conversation coordinator (queue-based or in-memory).
 * <p>
 * Matches EDDI v6's {@code CoordinatorStatus} record.
 *
 * @param coordinatorType coordinator type (e.g., "nats", "in-memory")
 * @param connected whether the coordinator is operational
 * @param connectionStatus detailed connection status string
 * @param activeConversations number of conversations with queued tasks
 * @param totalProcessed total tasks processed since startup
 * @param totalDeadLettered total dead-lettered tasks since startup
 * @param queueDepths per-conversation queue depths (conversationId → depth)
 */
public record CoordinatorStatus(
        String coordinatorType,
        boolean connected,
        String connectionStatus,
        int activeConversations,
        long totalProcessed,
        long totalDeadLettered,
        Map<String, Integer> queueDepths) {
}
