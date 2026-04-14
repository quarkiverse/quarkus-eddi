package io.quarkiverse.eddi;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link BoundedConversationMap} — verifies LRU eviction,
 * thread-safe access patterns, and capacity enforcement.
 */
class BoundedConversationMapTest {

    @Test
    void getReturnsNullForUnknownUser() {
        BoundedConversationMap map = new BoundedConversationMap(10);
        assertNull(map.get("unknown"));
    }

    @Test
    void putAndGetRoundtrip() {
        BoundedConversationMap map = new BoundedConversationMap(10);
        Conversation conv = new Conversation("agent-1", "conv-1", null, null);
        map.put("user-1", conv);
        assertSame(conv, map.get("user-1"));
        assertEquals(1, map.size());
    }

    @Test
    void lruEvictsOldestEntry() {
        BoundedConversationMap map = new BoundedConversationMap(3);

        map.put("user-1", new Conversation("a", "c1", null, null));
        map.put("user-2", new Conversation("a", "c2", null, null));
        map.put("user-3", new Conversation("a", "c3", null, null));

        assertEquals(3, map.size());

        // Adding a 4th should evict user-1 (LRU)
        map.put("user-4", new Conversation("a", "c4", null, null));

        assertEquals(3, map.size());
        assertNull(map.get("user-1"), "user-1 should have been evicted (LRU)");
        assertNotNull(map.get("user-2"));
        assertNotNull(map.get("user-3"));
        assertNotNull(map.get("user-4"));
    }

    @Test
    void accessRefreshesLruOrder() {
        BoundedConversationMap map = new BoundedConversationMap(3);

        map.put("user-1", new Conversation("a", "c1", null, null));
        map.put("user-2", new Conversation("a", "c2", null, null));
        map.put("user-3", new Conversation("a", "c3", null, null));

        // Access user-1 to move it to the "recently used" end
        map.get("user-1");

        // Now user-2 is the LRU — it should be evicted
        map.put("user-4", new Conversation("a", "c4", null, null));

        assertNotNull(map.get("user-1"), "user-1 was accessed recently, should NOT be evicted");
        assertNull(map.get("user-2"), "user-2 should have been evicted (LRU)");
        assertNotNull(map.get("user-3"));
        assertNotNull(map.get("user-4"));
    }

    @Test
    void clearRemovesAll() {
        BoundedConversationMap map = new BoundedConversationMap(10);
        map.put("user-1", new Conversation("a", "c1", null, null));
        map.put("user-2", new Conversation("a", "c2", null, null));
        assertEquals(2, map.size());

        map.clear();

        assertEquals(0, map.size());
        assertNull(map.get("user-1"));
        assertNull(map.get("user-2"));
    }

    @Test
    void getOrCreateCreatesNew() {
        BoundedConversationMap map = new BoundedConversationMap(10);
        Conversation created = new Conversation("a", "c1", null, null);

        Conversation result = map.getOrCreate("user-1", () -> created);
        assertSame(created, result);
        assertEquals(1, map.size());
    }

    @Test
    void getOrCreateReturnsExisting() {
        BoundedConversationMap map = new BoundedConversationMap(10);
        Conversation first = new Conversation("a", "c1", null, null);
        Conversation second = new Conversation("a", "c2", null, null);

        map.getOrCreate("user-1", () -> first);
        Conversation result = map.getOrCreate("user-1", () -> second);

        assertSame(first, result, "Should return existing, not create a new one");
        assertEquals(1, map.size());
    }

    @Test
    void sizeReflectsCurrentCount() {
        BoundedConversationMap map = new BoundedConversationMap(100);
        assertEquals(0, map.size());

        for (int i = 0; i < 50; i++) {
            map.put("user-" + i, new Conversation("a", "c" + i, null, null));
        }

        assertEquals(50, map.size());
    }
}
