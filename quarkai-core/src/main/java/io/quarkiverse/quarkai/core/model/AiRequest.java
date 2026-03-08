package io.quarkiverse.quarkai.core.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable AI request DTO containing all parameters for a single model call.
 *
 * <p>Build instances via the fluent {@link Builder}:
 * <pre>{@code
 * AiRequest request = AiRequest.builder()
 *     .model("gpt-4o")
 *     .addMessage(Message.system("You are a helpful assistant."))
 *     .addMessage(Message.user("Hello!"))
 *     .temperature(0.7)
 *     .maxTokens(512)
 *     .build();
 * }</pre>
 */
public final class AiRequest {

    private final String model;
    private final List<Message> messages;
    private final double temperature;
    private final int maxTokens;
    private final boolean stream;
    private final String userId;

    private AiRequest(Builder builder) {
        this.model       = Objects.requireNonNull(builder.model, "model must not be null");
        this.messages    = Collections.unmodifiableList(new ArrayList<>(builder.messages));
        this.temperature = builder.temperature;
        this.maxTokens   = builder.maxTokens;
        this.stream      = builder.stream;
        this.userId      = builder.userId;

        if (this.messages.isEmpty()) {
            throw new IllegalArgumentException("AiRequest must contain at least one message");
        }
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    /** The model identifier (e.g. {@code "gpt-4o"}, {@code "claude-3-opus-20240229"}). */
    public String model() {
        return model;
    }

    /** Ordered list of conversation messages. Never null, never empty. */
    public List<Message> messages() {
        return messages;
    }

    /** Sampling temperature (0.0–2.0). Default: {@code 1.0}. */
    public double temperature() {
        return temperature;
    }

    /** Maximum tokens in the completion. Default: {@code 1024}. */
    public int maxTokens() {
        return maxTokens;
    }

    /** Whether the provider should stream the response. */
    public boolean stream() {
        return stream;
    }

    /** Optional user ID for audit / abuse tracking. */
    public Optional<String> userId() {
        return Optional.ofNullable(userId);
    }

    // ── Builder ───────────────────────────────────────────────────────────────

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String model;
        private final List<Message> messages = new ArrayList<>();
        private double temperature = 1.0;
        private int maxTokens = 1024;
        private boolean stream = false;
        private String userId;

        private Builder() {}

        public Builder model(String model) {
            this.model = model;
            return this;
        }

        public Builder addMessage(Message message) {
            this.messages.add(Objects.requireNonNull(message, "message must not be null"));
            return this;
        }

        public Builder messages(List<Message> messages) {
            this.messages.clear();
            this.messages.addAll(Objects.requireNonNull(messages, "messages must not be null"));
            return this;
        }

        public Builder temperature(double temperature) {
            if (temperature < 0.0 || temperature > 2.0) {
                throw new IllegalArgumentException("temperature must be between 0.0 and 2.0, got: " + temperature);
            }
            this.temperature = temperature;
            return this;
        }

        public Builder maxTokens(int maxTokens) {
            if (maxTokens <= 0) {
                throw new IllegalArgumentException("maxTokens must be positive, got: " + maxTokens);
            }
            this.maxTokens = maxTokens;
            return this;
        }

        public Builder stream(boolean stream) {
            this.stream = stream;
            return this;
        }

        public Builder userId(String userId) {
            this.userId = userId;
            return this;
        }

        public AiRequest build() {
            return new AiRequest(this);
        }
    }

    // ── Object overrides ──────────────────────────────────────────────────────

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AiRequest r)) return false;
        return Double.compare(r.temperature, temperature) == 0
                && maxTokens == r.maxTokens
                && stream == r.stream
                && model.equals(r.model)
                && messages.equals(r.messages)
                && Objects.equals(userId, r.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(model, messages, temperature, maxTokens, stream, userId);
    }

    @Override
    public String toString() {
        return "AiRequest{model='" + model + "', messages=" + messages.size()
                + ", temperature=" + temperature + ", maxTokens=" + maxTokens
                + ", stream=" + stream + '}';
    }
}
