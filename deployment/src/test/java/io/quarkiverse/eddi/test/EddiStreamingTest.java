package io.quarkiverse.eddi.test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.quarkiverse.eddi.Conversation;
import io.quarkiverse.eddi.EddiClient;
import io.quarkiverse.eddi.EddiStreamException;
import io.quarkiverse.eddi.StreamListener;
import io.quarkiverse.eddi.model.ConversationResult;
import io.quarkiverse.eddi.model.ConversationState;
import io.quarkiverse.eddi.model.StreamToken;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;

/**
 * SSE streaming against a WireMock-simulated EDDI {@code /agents/{id}/stream}.
 */
@QuarkusTest
@QuarkusTestResource(WireMockEddiResource.class)
class EddiStreamingTest {

    @Inject
    EddiClient eddi;

    @Test
    void sayStreamingPreservesEventNames() {
        Conversation conv = eddi.agent("any-agent").startConversation();
        List<StreamToken> tokens = conv.sayStreaming("Hi").collect().asList().await().atMost(Duration.ofSeconds(10));

        assertEquals(List.of("task_start", "token", "token", "token", "task_complete", "done"),
                tokens.stream().map(StreamToken::type).toList());
        // EDDI pads each data line with one space; the client must strip exactly that one
        assertEquals("Hello from WireMock!",
                tokens.stream().filter(StreamToken::isToken).map(StreamToken::text).collect(Collectors.joining()));
    }

    @Test
    void listenerReceivesParsedEvents() throws Exception {
        Conversation conv = eddi.agent("any-agent").startConversation();
        List<String> calls = new CopyOnWriteArrayList<>();
        CountDownLatch done = new CountDownLatch(1);

        conv.sayStreaming("Hi", new StreamListener() {
            @Override
            public void onToken(String text) {
                calls.add("token");
            }

            @Override
            public void onTaskComplete(String taskId, String taskType, long durationMs) {
                calls.add("complete:" + taskId + "," + taskType + "," + durationMs);
            }

            @Override
            public void onComplete(ConversationResult result) {
                calls.add("done:" + result.conversationState());
                done.countDown();
            }

            @Override
            public void onError(Throwable error) {
                calls.add("error:" + error);
                done.countDown();
            }
        });

        assertTrue(done.await(10, TimeUnit.SECONDS));
        assertTrue(calls.contains("complete:1,llm,42"), calls.toString());
        assertTrue(calls.contains("done:" + ConversationState.READY), calls.toString());
    }

    @Test
    void generatedStreamEndpointRelaysEvents() {
        // Regression: the generated Multi endpoint must not start the conversation
        // (a blocking call) on the event loop.
        given()
                .contentType("text/plain")
                .body("Hi")
                .when()
                .post("/test-streaming-agent/stream")
                .then()
                .statusCode(200)
                .body(containsString("Hello"));
    }

    @Test
    void streamedPauseIsCompletedWithHitlDetails() throws Exception {
        Conversation conv = eddi.agent("hitl-agent").conversation("conv-hitl-001");
        CompletableFuture<ConversationResult> done = new CompletableFuture<>();
        List<String> tools = new CopyOnWriteArrayList<>();
        conv.sayStreaming("Look up my order", new StreamListener() {
            @Override
            public void onToolCall(String toolName) {
                tools.add(toolName);
            }

            @Override
            public void onComplete(ConversationResult result) {
                done.complete(result);
            }

            @Override
            public void onError(Throwable error) {
                done.completeExceptionally(error);
            }
        });

        ConversationResult result = done.get(10, TimeUnit.SECONDS);
        assertEquals(List.of("lookupOrder"), tools);
        assertTrue(result.isAwaitingHuman());
        assertEquals("I need approval.", result.text());
        assertEquals("TOOL_CALL", result.hitlPauseType());
        assertEquals(List.of("lookupOrder"), result.hitlPendingToolNames());
        assertEquals(Instant.parse("2024-07-03T00:00:00.123Z"), result.hitlPausedAt());
    }

    @Test
    void errorEventArrivesAsTypedException() throws Exception {
        CompletableFuture<Throwable> failure = new CompletableFuture<>();
        eddi.agent("any").conversation("conv-busy-001").sayStreaming("hi", new StreamListener() {
            @Override
            public void onError(Throwable error) {
                failure.complete(error);
            }
        });

        Throwable error = failure.get(10, TimeUnit.SECONDS);
        EddiStreamException streamError = assertInstanceOf(EddiStreamException.class, error);
        assertTrue(streamError.isAwaitingApproval());
        assertEquals("Conversation is awaiting approval", streamError.getMessage());
    }

    @Test
    void groupDiscussionStreamCarriesJsonEvents() {
        List<StreamToken> events = eddi.group("group-1").discussStreaming("Monolith?")
                .collect().asList().await().atMost(Duration.ofSeconds(10));

        assertEquals(List.of("group_start", "speaker_complete", "group_complete"),
                events.stream().map(StreamToken::type).toList());
        assertEquals("{\"speaker\":\"Rosa\",\"response\":\"Monolith.\"}", events.get(1).text());
    }

    @Test
    void logStreamSkipsHeartbeatComments() {
        List<StreamToken> events = eddi.logs().stream(null, null, "INFO")
                .collect().asList().await().atMost(Duration.ofSeconds(10));

        assertEquals(1, events.size(), "heartbeat comment frames must not surface: " + events);
        assertEquals("log", events.get(0).type());
        assertTrue(events.get(0).text().contains("\"message\":\"hello\""));
    }
}
