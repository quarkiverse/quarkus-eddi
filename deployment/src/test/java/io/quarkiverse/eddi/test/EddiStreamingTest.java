package io.quarkiverse.eddi.test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.quarkiverse.eddi.Conversation;
import io.quarkiverse.eddi.EddiClient;
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
}
