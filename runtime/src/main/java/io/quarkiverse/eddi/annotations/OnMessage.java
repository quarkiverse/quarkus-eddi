package io.quarkiverse.eddi.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as a pre-processing hook that runs before each message is sent to EDDI.
 * <p>
 * Must be used inside a class annotated with {@link EddiAgent}.
 * The method receives an {@code EddiConversation} and the user's message string.
 * <p>
 * Example:
 *
 * <pre>{@code
 * @OnMessage
 * public void preProcess(EddiConversation conv, String message) {
 *     conv.addContext("language", detectLanguage(message));
 * }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OnMessage {
}
