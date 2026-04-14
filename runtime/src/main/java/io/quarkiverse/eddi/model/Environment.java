package io.quarkiverse.eddi.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Deployment environment for agents.
 * <p>
 * Matches EDDI v6's {@code Deployment.Environment}. Legacy values
 * ({@code restricted}, {@code unrestricted}) are mapped to {@link #PRODUCTION}
 * for backwards compatibility.
 */
public enum Environment {
    PRODUCTION("production"),
    TEST("test");

    private final String value;

    Environment(String value) {
        this.value = value;
    }

    @JsonValue
    public String toValue() {
        return value;
    }

    @JsonCreator
    public static Environment fromString(String value) {
        if (value == null) {
            return PRODUCTION;
        }
        return switch (value.toLowerCase()) {
            case "unrestricted", "restricted", "production" -> PRODUCTION;
            case "test" -> TEST;
            default -> PRODUCTION;
        };
    }
}
