package io.quarkiverse.eddi.model;

import java.util.Map;

/**
 * Input payload for conversation messages with optional context.
 * <p>
 * Matches EDDI v6's {@code ai.labs.eddi.engine.model.InputData}.
 *
 * @param input the user's text message
 * @param context optional context values keyed by name
 */
public record InputData(String input, Map<String, Context> context) {

    /**
     * Create an InputData with just a text message and no context.
     */
    public static InputData of(String input) {
        return new InputData(input, Map.of());
    }

    /**
     * Create an InputData with a text message and context.
     */
    public static InputData of(String input, Map<String, Context> context) {
        return new InputData(input, context != null ? context : Map.of());
    }
}
