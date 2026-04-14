package io.quarkiverse.eddi.model;

import java.util.Map;

/**
 * Deployment status for an agent in a specific environment.
 * <p>
 * Matches EDDI v6's {@code AgentDeploymentStatus}.
 *
 * @param environment the deployment environment
 * @param agentId the agent identifier
 * @param agentVersion the deployed version
 * @param status current deployment lifecycle status
 * @param descriptor the agent's document descriptor (name, description, timestamps, etc.)
 */
public record AgentDeploymentStatus(
        Environment environment,
        String agentId,
        Integer agentVersion,
        DeploymentStatus status,
        Map<String, Object> descriptor) {
}
