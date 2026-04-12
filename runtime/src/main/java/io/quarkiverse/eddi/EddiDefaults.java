package io.quarkiverse.eddi;

import java.time.Duration;

/**
 * Shared constants for the EDDI SDK.
 */
public final class EddiDefaults {

    private EddiDefaults() {
    }

    /**
     * Default timeout for blocking operations (30 seconds).
     * <p>
     * Used by {@link EddiClient}, {@link Conversation}, and
     * {@link ManagedConversation} for {@code .await().atMost()} calls.
     */
    public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

    /**
     * Maximum number of per-user conversations retained by generated
     * {@code @EddiAgent} endpoint resources before eviction kicks in.
     */
    public static final int MAX_CONVERSATIONS_PER_AGENT = 10_000;
}
