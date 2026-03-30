package io.quarkiverse.eddi.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * An in-memory or historical log entry from EDDI's log admin API.
 * <p>
 * Matches EDDI v6's {@code LogEntry} record. Uses {@code long} epoch millis
 * for timestamp (matching the server's format).
 *
 * @param timestamp epoch millis when the log was recorded
 * @param level log level (TRACE, DEBUG, INFO, WARN, ERROR)
 * @param loggerName the logger that produced this entry
 * @param message the log message
 * @param environment the deployment environment
 * @param agentId optional associated agent ID
 * @param agentVersion optional associated agent version
 * @param conversationId optional associated conversation ID
 * @param userId optional associated user ID
 * @param instanceId the EDDI instance that produced this entry
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LogEntry(
        long timestamp,
        String level,
        String loggerName,
        String message,
        String environment,
        String agentId,
        Integer agentVersion,
        String conversationId,
        String userId,
        String instanceId) {
}
