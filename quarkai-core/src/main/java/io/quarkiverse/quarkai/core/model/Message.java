package io.quarkiverse.quarkai.core.model;

import java.util.Objects;

/**
 * Represents a single message in a conversation with an AI model.
 *
 * <p>Instances are immutable. Use the static factory methods to create messages:
 * <pre>{@code
 * Message user   = Message.user("What is the capital of France?");
 * Message system = Message.system("You are a helpful assistant.");
 * Message ai     = Message.assistant("The capital of France is Paris.");
 * }</pre>
 */
public final class Message {

    /**
     * The role of the message sender.
     */
    public enum Role {
        SYSTEM,
        USER,
        ASSISTANT,
        TOOL
    }

    private final Role role;
    private final String content;

    private Message(Role role, String content) {
        this.role = Objects.requireNonNull(role, "role must not be null");
        this.content = Objects.requireNonNull(content, "content must not be null");
    }

    // ── Static factories ──────────────────────────────────────────────────────

    /** Creates a SYSTEM message. */
    public static Message system(String content) {
        return new Message(Role.SYSTEM, content);
    }

    /** Creates a USER message. */
    public static Message user(String content) {
        return new Message(Role.USER, content);
    }

    /** Creates an ASSISTANT message. */
    public static Message assistant(String content) {
        return new Message(Role.ASSISTANT, content);
    }

    /** Creates a TOOL message (for function-call results). */
    public static Message tool(String content) {
        return new Message(Role.TOOL, content);
    }

    /** Generic factory for any role. */
    public static Message of(Role role, String content) {
        return new Message(role, content);
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public Role role() {
        return role;
    }

    public String content() {
        return content;
    }

    // ── Object overrides ──────────────────────────────────────────────────────

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Message m)) return false;
        return role == m.role && content.equals(m.content);
    }

    @Override
    public int hashCode() {
        return Objects.hash(role, content);
    }

    @Override
    public String toString() {
        return "Message{role=" + role + ", content='" + content + "'}";
    }
}
