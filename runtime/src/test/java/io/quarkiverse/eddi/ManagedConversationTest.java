package io.quarkiverse.eddi;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ManagedConversation} — tests the public API contract
 * without requiring a running EDDI server or Mockito.
 */
class ManagedConversationTest {

    @Test
    void intentAccessor() {
        ManagedConversation mc = new ManagedConversation("support", "user-1", null);
        assertEquals("support", mc.intent());
    }

    @Test
    void userIdAccessor() {
        ManagedConversation mc = new ManagedConversation("billing", "user-42", null);
        assertEquals("user-42", mc.userId());
    }

    @Test
    void constructorStoresAllFields() {
        ManagedConversation mc = new ManagedConversation("onboarding", "new-user", null);
        assertEquals("onboarding", mc.intent());
        assertEquals("new-user", mc.userId());
        // managedClient is null — we can't call say() without it,
        // but the object is constructible for builder patterns
    }
}
