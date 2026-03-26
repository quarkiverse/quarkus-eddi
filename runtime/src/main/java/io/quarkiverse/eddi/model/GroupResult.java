package io.quarkiverse.eddi.model;

import java.util.List;
import java.util.Map;

/**
 * Result from a group discussion (multi-agent debate).
 */
public record GroupResult(
        String groupId,
        String groupConversationId,
        String question,
        List<Map<String, Object>> transcript,
        String synthesis) {
}
