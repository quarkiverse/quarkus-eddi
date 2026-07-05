package io.quarkiverse.eddi.model;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Tests for the typed model records to verify Jackson serialization
 * roundtrips and field correctness against the EDDI v6 server types.
 */
class ModelRecordTest {

    private final ObjectMapper mapper = new ObjectMapper();

    // --- Context ---

    @Test
    void contextOfString() {
        Context ctx = Context.of("hello");
        assertEquals(Context.ContextType.string, ctx.type());
        assertEquals("hello", ctx.value());
    }

    @Test
    void contextOfObject() {
        Map<String, String> map = Map.of("key", "val");
        Context ctx = Context.ofObject(map);
        assertEquals(Context.ContextType.object, ctx.type());
        assertEquals(map, ctx.value());
    }

    @Test
    void contextJsonRoundtrip() throws Exception {
        Context ctx = Context.of("test-value");
        String json = mapper.writeValueAsString(ctx);
        assertTrue(json.contains("\"type\":\"string\""));
        assertTrue(json.contains("\"value\":\"test-value\""));

        Context deserialized = mapper.readValue(json, Context.class);
        assertEquals(ctx, deserialized);
    }

    // --- InputData ---

    @Test
    void inputDataOfString() {
        InputData data = InputData.of("hello");
        assertEquals("hello", data.input());
        assertNotNull(data.context());
        assertTrue(data.context().isEmpty());
    }

    @Test
    void inputDataWithContext() {
        var ctx = Map.of("key", Context.of("val"));
        InputData data = new InputData("msg", ctx);
        assertEquals("msg", data.input());
        assertEquals(1, data.context().size());
    }

    @Test
    void inputDataJsonRoundtrip() throws Exception {
        InputData data = InputData.of("test message");
        String json = mapper.writeValueAsString(data);
        assertTrue(json.contains("\"input\":\"test message\""));

        InputData deserialized = mapper.readValue(json, InputData.class);
        assertEquals(data.input(), deserialized.input());
    }

    // --- ConversationState ---

    @Test
    void conversationStateValues() {
        // Must match EDDI v6's ConversationState exactly (six constants). A missing
        // constant breaks deserialization of a conversation in that state.
        assertEquals(6, ConversationState.values().length);
        assertNotNull(ConversationState.valueOf("READY"));
        assertNotNull(ConversationState.valueOf("IN_PROGRESS"));
        assertNotNull(ConversationState.valueOf("ERROR"));
        assertNotNull(ConversationState.valueOf("ENDED"));
        assertNotNull(ConversationState.valueOf("EXECUTION_INTERRUPTED"));
        // AWAITING_HUMAN — added by the v6 HITL framework.
        assertNotNull(ConversationState.valueOf("AWAITING_HUMAN"));
    }

    // --- Environment ---

    @Test
    void environmentValues() {
        assertEquals(2, Environment.values().length);
        assertNotNull(Environment.valueOf("PRODUCTION"));
        assertNotNull(Environment.valueOf("TEST"));
    }

    @Test
    void environmentJsonCreator() throws Exception {
        // v6 sends lowercase strings — @JsonCreator must handle
        Environment env = mapper.readValue("\"production\"", Environment.class);
        assertEquals(Environment.PRODUCTION, env);
    }

    // --- DeploymentStatus ---

    @Test
    void deploymentStatusValues() {
        assertEquals(4, DeploymentStatus.values().length);
        assertNotNull(DeploymentStatus.valueOf("READY"));
        assertNotNull(DeploymentStatus.valueOf("IN_PROGRESS"));
        assertNotNull(DeploymentStatus.valueOf("NOT_FOUND"));
        assertNotNull(DeploymentStatus.valueOf("ERROR"));
    }

    // --- ConversationResult ---

    @Test
    void conversationResultTextFallback() {
        ConversationResult result = new ConversationResult("id", null, List.of(), List.of(), List.of(),
                ConversationState.READY);
        assertEquals("", result.text());
    }

    @Test
    void conversationResultTextPresent() {
        ConversationResult result = new ConversationResult("id", "Hello!", List.of("Hello!"), List.of(), List.of(),
                ConversationState.READY);
        assertEquals("Hello!", result.text());
    }

    @Test
    void conversationResultIsEnded() {
        ConversationResult ended = new ConversationResult("id", "", List.of(), List.of(), List.of(), ConversationState.ENDED);
        assertTrue(ended.isEnded());

        ConversationResult active = new ConversationResult("id", "", List.of(), List.of(), List.of(), ConversationState.READY);
        assertFalse(active.isEnded());
    }

    // --- StreamToken ---

    @Test
    void streamTokenTypes() {
        StreamToken token = new StreamToken("token", "hello");
        assertTrue(token.isToken());
        assertFalse(token.isDone());
        assertFalse(token.isError());
        assertEquals("hello", token.text());

        StreamToken done = new StreamToken("done", "");
        assertTrue(done.isDone());
        assertFalse(done.isToken());

        StreamToken error = new StreamToken("error", "something failed");
        assertTrue(error.isError());
        assertEquals("something failed", error.text());
    }

    @Test
    void streamTokenLifecycleTypes() {
        StreamToken taskStart = new StreamToken("task_start", "{\"taskId\":\"1\"}");
        assertTrue(taskStart.isTaskStart());

        StreamToken taskComplete = new StreamToken("task_complete", "{\"taskId\":\"1\"}");
        assertTrue(taskComplete.isTaskComplete());
    }

    @Test
    void streamTokenTextNullSafe() {
        StreamToken token = new StreamToken("token", null);
        assertEquals("", token.text());
    }

    // --- SetupAgentRequest Builder ---

    @Test
    void setupAgentRequestBuilder() {
        SetupAgentRequest request = SetupAgentRequest.builder()
                .agentName("Test Bot")
                .systemPrompt("You are helpful.")
                .provider("openai")
                .model("gpt-4o")
                .deploy(true)
                .environment("production")
                .build();

        assertEquals("Test Bot", request.agentName());
        assertEquals("You are helpful.", request.systemPrompt());
        assertEquals("openai", request.provider());
        assertEquals("gpt-4o", request.model());
        assertTrue(request.deploy());
        assertEquals("production", request.environment());
    }

    @Test
    void setupAgentRequestJsonRoundtrip() throws Exception {
        SetupAgentRequest request = SetupAgentRequest.builder()
                .agentName("Bot").systemPrompt("Prompt").build();

        String json = mapper.writeValueAsString(request);
        assertTrue(json.contains("\"agentName\":\"Bot\""));
        assertTrue(json.contains("\"systemPrompt\":\"Prompt\""));
    }

    // --- CreateApiAgentRequest Builder ---

    @Test
    void createApiAgentRequestBuilder() {
        CreateApiAgentRequest request = CreateApiAgentRequest.builder()
                .agentName("API Bot")
                .systemPrompt("You call APIs.")
                .openApiSpec("openapi: 3.0.0\ninfo: {}")
                .provider("anthropic")
                .model("claude-4")
                .deploy(false)
                .build();

        assertEquals("API Bot", request.agentName());
        assertEquals("You call APIs.", request.systemPrompt());
        assertNotNull(request.openApiSpec());
        assertFalse(request.deploy());
    }

    // --- CoordinatorStatus (v6 field parity) ---

    @Test
    void coordinatorStatusFields() {
        CoordinatorStatus status = new CoordinatorStatus(
                "nats", true, "CONNECTED", 5, 1000L, 3L, Map.of("conv-1", 2));

        assertEquals("nats", status.coordinatorType());
        assertTrue(status.connected());
        assertEquals("CONNECTED", status.connectionStatus());
        assertEquals(5, status.activeConversations());
        assertEquals(1000L, status.totalProcessed());
        assertEquals(3L, status.totalDeadLettered());
        assertEquals(Map.of("conv-1", 2), status.queueDepths());
    }

    @Test
    void coordinatorStatusJsonRoundtrip() throws Exception {
        CoordinatorStatus status = new CoordinatorStatus("in-memory", true, "OK", 0, 0, 0, Map.of());
        String json = mapper.writeValueAsString(status);
        CoordinatorStatus deserialized = mapper.readValue(json, CoordinatorStatus.class);
        assertEquals(status, deserialized);
    }

    // --- DeadLetterEntry (v6 field parity) ---

    @Test
    void deadLetterEntryFields() {
        DeadLetterEntry entry = new DeadLetterEntry("dl-1", "conv-123", "NullPointerException", 1711900000000L,
                "{\"task\":\"data\"}");

        assertEquals("dl-1", entry.id());
        assertEquals("conv-123", entry.conversationId());
        assertEquals("NullPointerException", entry.error());
        assertEquals(1711900000000L, entry.timestamp());
        assertEquals("{\"task\":\"data\"}", entry.payload());
    }

    // --- LogEntry (v6 field parity) ---

    @Test
    void logEntryFields() {
        LogEntry log = new LogEntry(1711900000000L, "INFO", "ai.labs.eddi",
                "Processing conversation", "production", "agent-1", 3,
                "conv-1", "user-1", "instance-abc");

        assertEquals(1711900000000L, log.timestamp());
        assertEquals("INFO", log.level());
        assertEquals("production", log.environment());
        assertEquals("agent-1", log.agentId());
        assertEquals(3, log.agentVersion());
        assertEquals("user-1", log.userId());
        assertEquals("instance-abc", log.instanceId());
    }

    @Test
    void logEntryJsonNullFieldsOmitted() throws Exception {
        LogEntry log = new LogEntry(0L, "DEBUG", "test", "msg", null, null, null, null, null, null);
        String json = mapper.writeValueAsString(log);
        assertFalse(json.contains("environment"), "null fields should be omitted via @JsonInclude");
        assertFalse(json.contains("agentId"));
        assertFalse(json.contains("userId"));
    }

    // --- AgentDeploymentStatus ---

    @Test
    void agentDeploymentStatusFields() {
        AgentDeploymentStatus status = new AgentDeploymentStatus(
                Environment.PRODUCTION, "agent-1", 2, DeploymentStatus.READY, Map.of("name", "My Agent"));

        assertEquals(Environment.PRODUCTION, status.environment());
        assertEquals("agent-1", status.agentId());
        assertEquals(2, status.agentVersion());
        assertEquals(DeploymentStatus.READY, status.status());
        assertEquals("My Agent", status.descriptor().get("name"));
    }

    // --- SetupResult ---

    @Test
    void setupResultFields() {
        SetupResult result = new SetupResult("setup_complete", "agent-1", "Bot",
                "openai", "gpt-4o", true, "READY", null, null, true, false,
                Map.of("agent", "/agents/agent-1"));

        assertEquals("setup_complete", result.action());
        assertTrue(result.deployed());
        assertEquals("READY", result.deploymentStatus());
        assertTrue(result.quickRepliesEnabled());
        assertFalse(result.sentimentAnalysisEnabled());
    }

    // --- HITL models (v6 Human-in-the-Loop framework) ---

    @Test
    void hitlVerdictValues() {
        assertEquals(2, HitlVerdict.values().length);
        assertNotNull(HitlVerdict.valueOf("APPROVED"));
        assertNotNull(HitlVerdict.valueOf("REJECTED"));
    }

    @Test
    void hitlDecisionApproveFactory() {
        HitlDecision approve = HitlDecision.approve("looks good");
        assertEquals(HitlVerdict.APPROVED, approve.verdict());
        assertEquals("looks good", approve.note());
        assertNull(approve.toolDecisions());

        HitlDecision reject = HitlDecision.reject("nope");
        assertEquals(HitlVerdict.REJECTED, reject.verdict());
        assertEquals("nope", reject.note());
    }

    @Test
    void hitlDecisionSerializesVerdictAsUppercaseName() throws Exception {
        // The server parses verdicts case-insensitively but the SDK sends the
        // canonical uppercase name.
        String json = mapper.writeValueAsString(HitlDecision.approve(null));
        assertTrue(json.contains("\"verdict\":\"APPROVED\""), json);
        // note + toolDecisions are null → omitted via @JsonInclude(NON_NULL)
        assertFalse(json.contains("note"), json);
        assertFalse(json.contains("toolDecisions"), json);
        // decidedBy is set server-side and must never be part of the request body
        assertFalse(json.contains("decidedBy"), json);
    }

    @Test
    void hitlDecisionWithToolDecisionsSerializes() throws Exception {
        HitlDecision decision = HitlDecision.ofToolCalls(HitlVerdict.APPROVED, "partial",
                Map.of("call-1", ToolCallDecision.reject("unsafe")));
        String json = mapper.writeValueAsString(decision);
        assertTrue(json.contains("\"toolDecisions\""), json);
        assertTrue(json.contains("\"call-1\""), json);
        assertTrue(json.contains("\"REJECTED\""), json);
    }

    @Test
    void groupApprovalRequestSerializes() throws Exception {
        GroupApprovalRequest request = GroupApprovalRequest.of(HitlDecision.approve("go"));
        String json = mapper.writeValueAsString(request);
        assertTrue(json.contains("\"decision\""), json);
        assertTrue(json.contains("\"verdict\":\"APPROVED\""), json);
        // taskApprovals null → omitted
        assertFalse(json.contains("taskApprovals"), json);
    }

    @Test
    void pendingApprovalSummaryFields() {
        PendingApprovalSummary summary = new PendingApprovalSummary(
                "conv-1", "agent-1", null, "user-1", null,
                "tool approval required", "REJECT", null, "TOOL_CALL",
                List.of("lookupOrder"));

        assertEquals("conv-1", summary.conversationId());
        assertEquals("agent-1", summary.agentId());
        assertEquals("user-1", summary.userId());
        assertEquals("TOOL_CALL", summary.pauseType());
        assertTrue(summary.isToolCallPause());
        assertEquals(List.of("lookupOrder"), summary.toolNames());
    }

    @Test
    void pendingApprovalSummaryDeserializesFromServerShape() throws Exception {
        String serverJson = """
                {
                    "conversationId": "conv-1",
                    "agentId": "agent-1",
                    "userId": "user-1",
                    "pauseReason": "rule paused",
                    "timeoutPolicy": "REJECT",
                    "pauseType": "RULE",
                    "toolNames": []
                }
                """;
        PendingApprovalSummary summary = mapper.readValue(serverJson, PendingApprovalSummary.class);
        assertEquals("conv-1", summary.conversationId());
        assertEquals("RULE", summary.pauseType());
        assertFalse(summary.isToolCallPause());
    }
}
