package io.quarkiverse.eddi.model;

/**
 * Deployment lifecycle status.
 * <p>
 * Matches EDDI v6's {@code Deployment.Status}.
 */
public enum DeploymentStatus {
    READY,
    IN_PROGRESS,
    NOT_FOUND,
    ERROR
}
