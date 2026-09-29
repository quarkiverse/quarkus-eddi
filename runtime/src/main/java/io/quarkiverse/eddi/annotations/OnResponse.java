package io.quarkiverse.eddi.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as a post-processing hook that runs after EDDI returns a response.
 * <p>
 * Must be used inside a class annotated with {@link EddiAgent}. Applies to the
 * non-streaming endpoint only. Supported signatures:
 * <ul>
 * <li>{@code ConversationResult hook(ConversationResult result)} — the returned value replaces the result</li>
 * <li>{@code void hook(ConversationResult result)} — observe only</li>
 * </ul>
 * Any other signature fails the build.
 *
 * <pre>{@code
 * @OnResponse
 * public void postProcess(ConversationResult result) {
 *     metrics.recordResponse(result);
 * }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OnResponse {
}
