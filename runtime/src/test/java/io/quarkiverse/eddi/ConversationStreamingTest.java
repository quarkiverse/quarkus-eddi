package io.quarkiverse.eddi;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.quarkiverse.eddi.client.EddiAgentRestClient;
import io.quarkiverse.eddi.model.ConversationResult;
import io.quarkiverse.eddi.model.ConversationState;
import io.quarkiverse.eddi.model.StreamToken;
import io.smallrye.mutiny.Uni;

/**
 * Unit tests for {@link Conversation#dispatch(StreamToken, StreamListener)} — mapping
 * EDDI's SSE event payloads (as emitted by {@code RestAgentEngineStreaming}) onto
 * {@link StreamListener} callbacks.
 */
class ConversationStreamingTest {

    private final List<String> calls = new ArrayList<>();
    private final StreamListener recorder = new StreamListener() {
        @Override
        public void onToken(String text) {
            calls.add("token:" + text);
        }

        @Override
        public void onTaskStart(String taskId, String taskType, int index) {
            calls.add("start:" + taskId + "," + taskType + "," + index);
        }

        @Override
        public void onTaskComplete(String taskId, String taskType, long durationMs) {
            calls.add("complete:" + taskId + "," + taskType + "," + durationMs);
        }

        @Override
        public void onTaskFailed(String taskId, String taskType, long durationMs, String errorType, String error) {
            calls.add("failed:" + taskId + "," + taskType + "," + durationMs + "," + errorType + "," + error);
        }

        @Override
        public void onToolCall(String toolName) {
            calls.add("tool:" + toolName);
        }

        @Override
        public void onEvent(StreamToken event) {
            calls.add("event:" + event.type());
        }

        @Override
        public void onComplete(ConversationResult result) {
            calls.add("done:" + result.conversationState() + "," + result.text() + "," + result.hitlPauseType()
                    + "," + result.hitlPendingToolNames());
        }

        @Override
        public void onError(Throwable error) {
            String code = error instanceof EddiStreamException e ? e.code() : "?";
            calls.add("error:" + code + "," + error.getMessage());
        }
    };

    private final Conversation conv = new Conversation("conv-1", "agent-1", null, null);

    @Test
    void taskLifecycleEventsAreParsedFromJson() {
        conv.dispatch(new StreamToken("task_start", "{\"taskId\":\"llm\",\"taskType\":\"ai.labs.llm\",\"index\":2}"),
                recorder);
        conv.dispatch(new StreamToken("task_complete",
                "{\"taskId\":\"llm\",\"taskType\":\"ai.labs.llm\",\"durationMs\":42,\"actions\":[\"a\"]}"), recorder);
        conv.dispatch(new StreamToken("task_failed",
                "{\"taskId\":\"http\",\"taskType\":\"ai.labs.httpcalls\",\"durationMs\":7,\"errorType\":\"TIMEOUT\",\"error\":\"slow\"}"),
                recorder);

        assertEquals(List.of(
                "start:llm,ai.labs.llm,2",
                "complete:llm,ai.labs.llm,42",
                "failed:http,ai.labs.httpcalls,7,TIMEOUT,slow"), calls);
    }

    @Test
    void tokensToolCallsAndOtherEvents() {
        conv.dispatch(new StreamToken("token", "Hel"), recorder);
        conv.dispatch(new StreamToken("tool_call", "{\"tool\":\"lookupOrder\"}"), recorder);
        conv.dispatch(new StreamToken("cascade_step_start", "{\"stepIndex\":0}"), recorder);

        assertEquals(List.of("token:Hel", "tool:lookupOrder", "event:cascade_step_start"), calls);
    }

    @Test
    void errorEventCarriesMachineReadableCode() {
        conv.dispatch(new StreamToken("error",
                "{\"message\":\"Conversation is awaiting approval\",\"code\":\"awaiting_approval\"}"), recorder);
        conv.dispatch(new StreamToken("error", "{\"message\":\"Internal server error\"}"), recorder);
        conv.dispatch(new StreamToken("error", "plain text"), recorder);

        assertEquals(List.of(
                "error:awaiting_approval,Conversation is awaiting approval",
                "error:null,Internal server error",
                "error:null,plain text"), calls);
        assertTrue(Conversation.parseErrorEvent("{\"code\":\"awaiting_approval\",\"message\":\"x\"}")
                .isAwaitingApproval());
    }

    @Test
    void doneEventCompletesWithResult() {
        conv.dispatch(new StreamToken("done",
                "{\"conversationState\":\"READY\",\"conversationOutputs\":[{\"output\":[{\"type\":\"text\",\"text\":\"Hi\"}]}]}"),
                recorder);

        assertEquals(List.of("done:READY,Hi,null,[]"), calls);
    }

    @Test
    void pausedDoneEventIsCompletedWithHitlDetails() {
        // The streamed done payload lacks hitlPauseType / pending tools; they are read
        // from GET /agents/{conversationId}.
        EddiAgentRestClient agentClient = (EddiAgentRestClient) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[] { EddiAgentRestClient.class },
                (proxy, method, args) -> {
                    assertEquals("readConversation", method.getName());
                    return Uni.createFrom().item(Map.<String, Object> of(
                            "conversationState", "AWAITING_HUMAN",
                            "hitlPauseType", "TOOL_CALL",
                            "hitlPendingToolCalls", Map.of("calls", List.of(Map.of("toolName", "lookupOrder")))));
                });
        Conversation paused = new Conversation("conv-1", "agent-1", agentClient, null);

        paused.dispatch(new StreamToken("done",
                "{\"conversationState\":\"AWAITING_HUMAN\",\"hitlPausedAt\":\"2026-09-29T10:00:00Z\","
                        + "\"conversationOutputs\":[{\"output\":[{\"type\":\"text\",\"text\":\"Need approval\"}]}]}"),
                recorder);

        assertEquals(List.of("done:" + ConversationState.AWAITING_HUMAN + ",Need approval,TOOL_CALL,[lookupOrder]"),
                calls);
    }
}
