package io.quarkiverse.eddi.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as a post-processing hook that runs after EDDI returns a response.
 * <p>
 * Must be used inside a class annotated with {@link EddiAgent}.
 * The method receives an {@code EddiConversation} and the {@code ConversationResult}.
 * <p>
 * Example:
 *
 * <pre>{@code
 * @OnResponse
 * public void postProcess(EddiConversation conv, ConversationResult result) {
 *     metricsService.recordResponse(conv.agentId(), result);
 * }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OnResponse {
}
