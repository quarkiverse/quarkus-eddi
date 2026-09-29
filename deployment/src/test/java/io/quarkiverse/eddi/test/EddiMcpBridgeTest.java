package io.quarkiverse.eddi.test;

import static org.junit.jupiter.api.Assertions.*;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.quarkiverse.mcp.server.ToolManager;
import io.quarkiverse.mcp.server.ToolManager.ToolArgument;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Verifies {@code @EddiTool} methods are registered with the Quarkus MCP server
 * with their name, description and argument metadata.
 */
@QuarkusTest
@QuarkusTestResource(WireMockEddiResource.class)
class EddiMcpBridgeTest {

    @Inject
    ToolManager toolManager;

    @Test
    void toolIsRegisteredWithDescriptionAndArguments() {
        ToolManager.ToolInfo tool = toolManager.getTool("lookupOrder");
        assertNotNull(tool, "@EddiTool method should be an MCP tool");
        assertEquals("Look up order status by order ID", tool.description());

        ToolArgument arg = tool.arguments().get(0);
        assertEquals("orderId", arg.name());
        assertEquals("The order ID", arg.description());
        assertTrue(arg.required());
    }

    @Test
    void nameOverrideAndOptionalArgument() {
        assertNull(toolManager.getTool("countItems"));
        ToolManager.ToolInfo tool = toolManager.getTool("count_items");
        assertNotNull(tool);
        assertFalse(tool.arguments().get(0).required());
    }
}
