package io.quarkiverse.eddi.model;

/**
 * Deployment status for an agent in a specific environment.
 * <p>
 * Matches EDDI v6's {@code AgentDeploymentStatus}.
 *
 * @param environment the deployment environment
 * @param agentId the agent identifier
 * @param agentVersion the deployed version
 * @param status current deployment lifecycle status
 * @param descriptorName optional human-readable agent name from the descriptor
 */
public record AgentDeploymentStatus(
        Environment environment,
        String agentId,
        Integer agentVersion,
        DeploymentStatus status,
        String descriptorName) {
}
