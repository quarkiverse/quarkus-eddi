package io.quarkiverse.eddi.deployment;

import io.quarkiverse.eddi.annotations.EddiTool;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;
import org.jboss.jandex.AnnotationInstance;
import org.jboss.jandex.DotName;
import org.jboss.logging.Logger;

/**
 * Build step processor that scans for {@link EddiTool} annotations and
 * auto-registers them as MCP tools via the Quarkus MCP Server extension.
 * <p>
 * This creates a bidirectional integration:
 * <ul>
 *   <li>Your app → EDDI (via EddiClient / REST)</li>
 *   <li>EDDI → Your app (via MCP tool invocation)</li>
 * </ul>
 */
public class EddiMcpBridgeProcessor {

    private static final Logger LOG = Logger.getLogger(EddiMcpBridgeProcessor.class);
    private static final DotName EDDI_TOOL = DotName.createSimple(EddiTool.class.getName());

    @BuildStep
    void processEddiToolAnnotations(CombinedIndexBuildItem combinedIndex) {

        var annotations = combinedIndex.getIndex().getAnnotations(EDDI_TOOL);

        for (AnnotationInstance annotation : annotations) {
            String description = annotation.value("description").asString();
            var nameValue = annotation.value("name");
            String name = (nameValue != null && !nameValue.asString().isEmpty())
                    ? nameValue.asString()
                    : annotation.target().asMethod().name();

            String methodName = annotation.target().asMethod().declaringClass().name()
                    + "#" + annotation.target().asMethod().name();

            LOG.infof("Discovered @EddiTool: %s → name=%s, description=%s",
                    methodName, name, description);

            // TODO: Phase 3 — Generate MCP tool registration:
            // 1. Create JSON Schema for method parameters from @ToolArg annotations
            // 2. Register the tool with quarkus-mcp-server
            // 3. On EDDI startup, auto-register the MCP server URL with configured agents
        }
    }
}
