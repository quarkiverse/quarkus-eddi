package io.quarkiverse.eddi;

/**
 * An {@code error} event received on an EDDI SSE stream.
 * <p>
 * EDDI sends {@code {"message": "...", "code": "..."}}. The {@link #code()} is the
 * machine-readable part, e.g. {@code awaiting_approval}, {@code conversation_not_found},
 * {@code conversation_ended}, {@code agent_not_ready}, {@code quota_exceeded};
 * it is {@code null} for opaque internal errors.
 */
public class EddiStreamException extends RuntimeException {

    private final String code;

    public EddiStreamException(String code, String message) {
        super(message);
        this.code = code;
    }

    /**
     * The machine-readable error code, or {@code null} if EDDI did not send one.
     */
    public String code() {
        return code;
    }

    /**
     * Whether the message was refused because the conversation is paused awaiting
     * a human approval decision.
     */
    public boolean isAwaitingApproval() {
        return "awaiting_approval".equals(code);
    }
}
