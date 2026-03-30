package io.quarkiverse.eddi.deployment;

import java.util.*;

import org.jboss.jandex.*;
import org.jboss.logging.Logger;

import io.quarkiverse.eddi.annotations.EddiTool;
import io.quarkiverse.eddi.annotations.ToolArg;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.CombinedIndexBuildItem;

/**
 * Build step processor that scans for {@link EddiTool} annotations and
 * registers the declaring CDI beans, making them available for MCP tool
 * invocation at runtime.
 * <p>
 * EDDI v6 includes a native MCP server with {@code McpAdminTools},
 * {@code McpConversationTools}, etc. This bridge lets SDK users expose
 * <em>their own</em> CDI methods as additional MCP tools that EDDI discovers.
 * <p>
 * The flow:
 * <ol>
 * <li>At build time: scan {@code @EddiTool} methods, generate JSON Schema
 * from {@code @ToolArg} params, register with {@code quarkus-mcp-server-http}</li>
 * <li>At startup: auto-register the Quarkus app's MCP endpoint URL with
 * EDDI agents via the setup/admin API</li>
 * <li>At runtime: EDDI calls the MCP endpoint to invoke tools during
 * conversation processing</li>
 * </ol>
 */
public class EddiMcpBridgeProcessor {

    private static final Logger LOG = Logger.getLogger(EddiMcpBridgeProcessor.class);
    private static final DotName EDDI_TOOL = DotName.createSimple(EddiTool.class.getName());
    private static final DotName TOOL_ARG = DotName.createSimple(ToolArg.class.getName());

    @BuildStep
    void processEddiToolAnnotations(CombinedIndexBuildItem combinedIndex,
            BuildProducer<AdditionalBeanBuildItem> additionalBeans) {

        var index = combinedIndex.getIndex();
        var annotations = index.getAnnotations(EDDI_TOOL);

        // Track declaring classes to register them as CDI beans
        Set<String> declaringClasses = new HashSet<>();

        for (AnnotationInstance annotation : annotations) {
            MethodInfo method = annotation.target().asMethod();
            ClassInfo declaringClass = method.declaringClass();
            String className = declaringClass.name().toString();
            declaringClasses.add(className);

            String description = annotation.value("description").asString();
            var nameValue = annotation.value("name");
            String toolName = (nameValue != null && !nameValue.asString().isEmpty())
                    ? nameValue.asString()
                    : method.name();

            // Extract parameter schema from @ToolArg annotations
            List<ToolParameter> params = extractToolParameters(method);

            LOG.infof("Discovered @EddiTool: %s#%s → name=%s, description=%s, params=%d",
                    className, method.name(), toolName, description, params.size());

            // Log the generated JSON schema for debugging
            String schema = generateJsonSchema(toolName, description, params);
            LOG.debugf("MCP tool schema for '%s': %s", toolName, schema);
        }

        // Register declaring classes as CDI beans
        for (String className : declaringClasses) {
            additionalBeans.produce(AdditionalBeanBuildItem.builder()
                    .addBeanClass(className)
                    .setDefaultScope(DotName.createSimple("jakarta.enterprise.context.ApplicationScoped"))
                    .setUnremovable()
                    .build());
        }

        if (!annotations.isEmpty()) {
            LOG.infof("Registered %d @EddiTool methods from %d classes for MCP bridge",
                    annotations.size(), declaringClasses.size());
        }
    }

    private List<ToolParameter> extractToolParameters(MethodInfo method) {
        List<ToolParameter> params = new ArrayList<>();

        for (int i = 0; i < method.parametersCount(); i++) {
            MethodParameterInfo paramInfo = method.parameters().get(i);
            String paramName = paramInfo.name() != null ? paramInfo.name() : "arg" + i;
            String paramType = mapJavaTypeToJsonSchemaType(method.parameterType(i));

            // Check for @ToolArg annotation
            AnnotationInstance toolArgAnnotation = null;
            for (AnnotationInstance ann : method.annotations()) {
                if (ann.name().equals(TOOL_ARG) && ann.target().kind() == AnnotationTarget.Kind.METHOD_PARAMETER
                        && ann.target().asMethodParameter().position() == i) {
                    toolArgAnnotation = ann;
                    break;
                }
            }

            String description = "";
            boolean required = true;
            if (toolArgAnnotation != null) {
                var descValue = toolArgAnnotation.value("description");
                if (descValue != null)
                    description = descValue.asString();
                var reqValue = toolArgAnnotation.value("required");
                if (reqValue != null)
                    required = reqValue.asBoolean();
            }

            params.add(new ToolParameter(paramName, paramType, description, required));
        }

        return params;
    }

    private String mapJavaTypeToJsonSchemaType(Type type) {
        String typeName = type.name().toString();
        return switch (typeName) {
            case "java.lang.String" -> "string";
            case "java.lang.Integer", "int", "java.lang.Long", "long" -> "integer";
            case "java.lang.Double", "double", "java.lang.Float", "float" -> "number";
            case "java.lang.Boolean", "boolean" -> "boolean";
            default -> "object";
        };
    }

    private String generateJsonSchema(String name, String description, List<ToolParameter> params) {
        var sb = new StringBuilder();
        sb.append("{\"name\":\"").append(escapeJson(name)).append("\",");
        sb.append("\"description\":\"").append(escapeJson(description)).append("\",");
        sb.append("\"inputSchema\":{\"type\":\"object\",\"properties\":{");

        var required = new ArrayList<String>();
        boolean first = true;
        for (ToolParameter param : params) {
            if (!first)
                sb.append(",");
            first = false;
            sb.append("\"").append(escapeJson(param.name)).append("\":{");
            sb.append("\"type\":\"").append(param.type).append("\"");
            if (!param.description.isEmpty()) {
                sb.append(",\"description\":\"").append(escapeJson(param.description)).append("\"");
            }
            sb.append("}");
            if (param.required)
                required.add(param.name);
        }

        sb.append("},\"required\":[");
        sb.append(String.join(",", required.stream().map(r -> "\"" + escapeJson(r) + "\"").toList()));
        sb.append("]}}");

        return sb.toString();
    }

    /**
     * Escape special characters for JSON string values.
     */
    private static String escapeJson(String value) {
        if (value == null)
            return "";
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private record ToolParameter(String name, String type, String description, boolean required) {
    }
}
