package io.quarkiverse.eddi.test;

import java.util.List;

import io.quarkiverse.eddi.annotations.EddiTool;
import io.quarkiverse.eddi.annotations.ToolArg;

/**
 * Test fixture for the @EddiTool → MCP bridge. Deliberately has no bean-defining
 * annotation: the bridge must register it.
 */
public class SampleTools {

    @EddiTool(description = "Look up order status by order ID")
    public String lookupOrder(@ToolArg(description = "The order ID") String orderId) {
        return "order " + orderId + " shipped";
    }

    @EddiTool(name = "count_items", description = "Count items")
    public String countItems(@ToolArg(description = "Items", required = false) List<String> items) {
        return String.valueOf(items == null ? 0 : items.size());
    }
}
