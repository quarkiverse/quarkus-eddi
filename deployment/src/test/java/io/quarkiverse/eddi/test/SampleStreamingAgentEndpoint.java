package io.quarkiverse.eddi.test;

import io.quarkiverse.eddi.annotations.EddiAgent;

/**
 * Test fixture: an {@code @EddiAgent} with the SSE {@code /stream} endpoint enabled.
 */
@EddiAgent(id = "test-streaming-agent", path = "/test-streaming-agent", streaming = true)
public class SampleStreamingAgentEndpoint {
}
