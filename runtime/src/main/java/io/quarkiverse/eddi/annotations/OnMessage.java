package io.quarkiverse.eddi.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as a pre-processing hook that runs before each message is sent to EDDI.
 * <p>
 * Must be used inside a class annotated with {@link EddiAgent}. Supported signatures:
 * <ul>
 * <li>{@code String hook(String message)} — the returned value replaces the message</li>
 * <li>{@code void hook(String message)} — observe only</li>
 * </ul>
 * Any other signature fails the build.
 *
 * <pre>{@code
 * @OnMessage
 * public String preProcess(String message) {
 *     return message.strip();
 * }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OnMessage {
}
