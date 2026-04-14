package io.quarkiverse.eddi.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as an MCP tool that EDDI agents can discover and invoke.
 * <p>
 * Methods annotated with {@code @EddiTool} are automatically registered as
 * MCP tools via the Quarkus MCP Server extension. EDDI discovers them and
 * can call them during conversation processing.
 * <p>
 * Example:
 *
 * <pre>{@code
 * @EddiTool(description = "Look up order status by order ID")
 * public OrderStatus lookupOrder(@ToolArg(description = "The order ID") String orderId) {
 *     return orderService.find(orderId);
 * }
 * }</pre>
 *
 * <p>
 * Configure which agents can see these tools via:
 * {@code quarkus.eddi.mcp-bridge.agents=support-bot,sales-bot}
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface EddiTool {

    /**
     * Human-readable description of what this tool does.
     * Used by EDDI's LLM to decide when to invoke it.
     */
    String description();

    /**
     * Optional tool name override. Defaults to the method name.
     */
    String name() default "";
}
