package io.quarkiverse.eddi.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotates a parameter of an {@link EddiTool}-annotated method to provide
 * metadata for MCP tool schema generation.
 * <p>
 * Example:
 *
 * <pre>{@code
 * @EddiTool(description = "Look up order status")
 * public OrderStatus lookupOrder(@ToolArg(description = "The order ID") String orderId) {
 *     return orderService.find(orderId);
 * }
 * }</pre>
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface ToolArg {

    /**
     * Human-readable description of this parameter.
     */
    String description() default "";

    /**
     * Whether this parameter is required. Defaults to true.
     */
    boolean required() default true;
}
