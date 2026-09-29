package io.quarkiverse.eddi.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declaratively wire an EDDI agent to a REST/SSE endpoint in your Quarkus app.
 * <p>
 * At build time, the deployment module scans for classes annotated with {@code @EddiAgent}
 * and generates JAX-RS resources that proxy requests to the specified EDDI agent.
 * <p>
 * Example:
 *
 * <pre>
 * &#64;EddiAgent(id = "support-bot", path = "/api/support")
 * public class SupportEndpoint {
 *
 *     &#64;OnMessage
 *     public String preProcess(String message) {
 *         return message.strip();
 *     }
 * }
 * </pre>
 *
 * This generates:
 * <ul>
 * <li>{@code POST /api/support} — sends a message and returns the full response</li>
 * <li>{@code POST /api/support/stream} — SSE streaming variant (unless {@code streaming = false})</li>
 * </ul>
 * Both accept a {@code text/plain} body and an optional {@code userId} query parameter
 * (default {@code anonymous}); each user keeps one EDDI conversation, started with that userId.
 * <p>
 * <b>Security:</b> the generated endpoints do not authenticate callers and trust the
 * {@code userId} query parameter as given. Protect the path with your own authentication,
 * or call {@code EddiClient} from your own resource if the id must come from the principal.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface EddiAgent {

    /**
     * EDDI agent ID to proxy requests to.
     */
    String id();

    /**
     * REST path for the generated endpoint.
     */
    String path();

    /**
     * Whether to generate an SSE streaming endpoint alongside the REST endpoint.
     */
    boolean streaming() default true;

    /**
     * Environment to use for this agent.
     * Defaults to the global {@code quarkus.eddi.environment} value.
     */
    String environment() default "";
}
