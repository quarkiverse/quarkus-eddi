package io.quarkiverse.eddi.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declaratively wire an EDDI agent to a REST/SSE endpoint in your Quarkus app.
 * <p>
 * <b>⚠️ Experimental:</b> This feature uses Gizmo bytecode generation to create JAX-RS
 * resources at build time. Due to a known limitation in RESTEasy Reactive
 * ({@code GeneratedBeanBuildItem} classes are not recognized as application archive resources),
 * the generated endpoints may not be fully functional in all deployment scenarios.
 * This is tracked for resolution in a future release.
 * <p>
 * At build time, the deployment module scans for classes annotated with {@code @EddiAgent}
 * and generates JAX-RS resources that proxy requests to the specified EDDI agent.
 * <p>
 * Example:
 *
 * <pre>
 * {
 *     &#64;code
 *     &#64;EddiAgent(id = "support-bot", path = "/api/support", streaming = true)
 *     public class SupportEndpoint {
 *
 *         &#64;OnMessage
 *         public void preProcess(EddiConversation conv, String message) {
 *             conv.addContext("department", "engineering");
 *         }
 *     }
 * }
 * </pre>
 *
 * This generates:
 * <ul>
 * <li>{@code POST /api/support} — sends a message and returns the full response</li>
 * <li>{@code POST /api/support/stream} — SSE streaming variant (if {@code streaming = true})</li>
 * </ul>
 *
 * @see <a href="https://github.com/quarkiverse/quarkus-eddi/issues">Tracking issue</a>
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface EddiAgent {

    /**
     * EDDI agent ID to proxy requests to.
     * Can be overridden via {@code quarkus.eddi.agents.<ClassName>.id}.
     */
    String id();

    /**
     * REST path for the generated endpoint.
     * Can be overridden via {@code quarkus.eddi.agents.<ClassName>.path}.
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
