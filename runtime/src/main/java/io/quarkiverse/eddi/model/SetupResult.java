package io.quarkiverse.eddi.model;

import java.util.List;
import java.util.Map;

/**
 * Result of an agent setup operation (both standard and API agent).
 * <p>
 * Matches EDDI v6's {@code SetupResult} record.
 *
 * @param action operation type ("setup_complete", "api_agent_created")
 * @param agentId the created agent's ID
 * @param agentName the agent name
 * @param provider the LLM provider used
 * @param model the model used
 * @param deployed whether the agent was successfully deployed
 * @param deploymentStatus deployment status (READY, IN_PROGRESS, etc.)
 * @param endpointCount number of API endpoints (API agent only)
 * @param groups API tag groups (API agent only)
 * @param quickRepliesEnabled whether quick replies are enabled
 * @param sentimentAnalysisEnabled whether sentiment analysis is enabled
 * @param resources map of created resource locations
 */
public record SetupResult(
        String action,
        String agentId,
        String agentName,
        String provider,
        String model,
        Boolean deployed,
        String deploymentStatus,
        Integer endpointCount,
        List<String> groups,
        Boolean quickRepliesEnabled,
        Boolean sentimentAnalysisEnabled,
        Map<String, Object> resources) {
}
