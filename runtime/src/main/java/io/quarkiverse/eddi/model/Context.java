package io.quarkiverse.eddi.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * A typed context value for conversations.
 * <p>
 * Matches EDDI v6's {@code ai.labs.eddi.engine.model.Context}.
 * <p>
 * Example usage:
 *
 * <pre>{@code
 * var ctx = Map.of(
 *         "userId", Context.of("user-123"),
 *         "preferences", Context.ofObject(Map.of("lang", "en")));
 * conv.sayWithContext("Hello", ctx);
 * }</pre>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Context(ContextType type, Object value, Boolean secret) {

    public Context(ContextType type, Object value) {
        this(type, value, null);
    }

    /**
     * Create a secret string context value — e.g. the caller's token for a downstream
     * API. EDDI can use it in templates and behavior rules for this turn only; it is
     * never persisted, returned or audited.
     */
    public static Context secret(String value) {
        return new Context(ContextType.string, value, true);
    }

    /**
     * The type of context value.
     */
    public enum ContextType {
        string,
        expressions,
        object,
        array
    }

    /**
     * Create a string context value.
     */
    public static Context of(String value) {
        return new Context(ContextType.string, value);
    }

    /**
     * Create an object context value (maps, POJOs, etc.).
     */
    public static Context ofObject(Object value) {
        return new Context(ContextType.object, value);
    }

    /**
     * Create an expressions context value.
     */
    public static Context ofExpressions(String expressions) {
        return new Context(ContextType.expressions, expressions);
    }

    /**
     * Create an array context value.
     */
    public static Context ofArray(Object value) {
        return new Context(ContextType.array, value);
    }
}
