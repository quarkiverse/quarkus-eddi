package io.quarkiverse.eddi;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link EddiClient} utility methods.
 */
class EddiClientTest {

    // --- extractIdFromUri ---

    @Test
    void extractIdFromUri_validAbsoluteUri() {
        String id = EddiClient.extractIdFromUri("http://localhost:7070/agents/abc-123");
        assertEquals("abc-123", id);
    }

    @Test
    void extractIdFromUri_validRelativeUri() {
        String id = EddiClient.extractIdFromUri("/agents/conv-456/start");
        assertEquals("start", id);
    }

    @Test
    void extractIdFromUri_uriWithTrailingSlash() {
        // Should skip the trailing empty segment and return the last real segment
        String id = EddiClient.extractIdFromUri("http://localhost:7070/agents/abc-123/");
        assertEquals("abc-123", id);
    }

    @Test
    void extractIdFromUri_singleSegment() {
        String id = EddiClient.extractIdFromUri("/conv-789");
        assertEquals("conv-789", id);
    }

    @Test
    void extractIdFromUri_multipleTrailingSlashes() {
        String id = EddiClient.extractIdFromUri("/agents/xyz///");
        assertEquals("xyz", id);
    }

    @Test
    void extractIdFromUri_nullThrows() {
        assertThrows(IllegalStateException.class, () -> EddiClient.extractIdFromUri(null));
    }

    @Test
    void extractIdFromUri_blankThrows() {
        assertThrows(IllegalStateException.class, () -> EddiClient.extractIdFromUri("   "));
    }

    @Test
    void extractIdFromUri_emptyThrows() {
        assertThrows(IllegalStateException.class, () -> EddiClient.extractIdFromUri(""));
    }

    @Test
    void extractIdFromUri_rootOnlyThrows() {
        assertThrows(IllegalStateException.class, () -> EddiClient.extractIdFromUri("/"));
    }
}
