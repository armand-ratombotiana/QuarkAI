package io.quarkiverse.quarkai.core.model;

import java.util.Objects;
import java.util.Optional;

/**
 * Immutable AI response DTO.
 *
 * <p>For streaming, each emission carries a partial content chunk and a null
 * finish reason. The final chunk carries the finish reason.
 *
 * <p>Usage:
 * <pre>{@code
 * chatModel.chat(request)
 *     .subscribe().with(response -> {
 *         System.out.println(response.content());
 *         System.out.println("Tokens used: " + response.usage());
 *     });
 * }</pre>
 */
public final class AiResponse {

    /**
     * Why the model stopped generating tokens.
     */
    public enum FinishReason {
        /** Natural end-of-generation. */
        STOP,
        /** Token limit reached. */
        LENGTH,
        /** Content policy violation. */
        CONTENT_FILTER,
        /** Function/tool call requested. */
        TOOL_CALLS,
        /** Stream chunk — not the final message. */
        NULL
    }

    private final String content;
    private final FinishReason finishReason;
    private final TokenUsage usage;
    private final String model;
    private final String id;

    private AiResponse(Builder builder) {
        this.content      = builder.content != null ? builder.content : "";
        this.finishReason = builder.finishReason != null ? builder.finishReason : FinishReason.NULL;
        this.usage        = builder.usage;
        this.model        = builder.model;
        this.id           = builder.id;
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    /** The text content of this response (or partial chunk in streaming). */
    public String content() {
        return content;
    }

    /** The reason the model stopped. {@link FinishReason#NULL} for stream chunks. */
    public FinishReason finishReason() {
        return finishReason;
    }

    /** Token usage — may be absent for streaming chunks. */
    public Optional<TokenUsage> usage() {
        return Optional.ofNullable(usage);
    }

    /** Model identifier used for this response. */
    public Optional<String> model() {
        return Optional.ofNullable(model);
    }

    /** Provider-assigned response ID. */
    public Optional<String> id() {
        return Optional.ofNullable(id);
    }

    /** Returns true if this is a terminal (non-chunk) response. */
    public boolean isFinished() {
        return finishReason != FinishReason.NULL;
    }

    // ── Builder ───────────────────────────────────────────────────────────────

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String content;
        private FinishReason finishReason;
        private TokenUsage usage;
        private String model;
        private String id;

        private Builder() {}

        public Builder content(String content) {
            this.content = content;
            return this;
        }

        public Builder finishReason(FinishReason finishReason) {
            this.finishReason = finishReason;
            return this;
        }

        public Builder usage(TokenUsage usage) {
            this.usage = usage;
            return this;
        }

        public Builder model(String model) {
            this.model = model;
            return this;
        }

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public AiResponse build() {
            return new AiResponse(this);
        }
    }

    // ── Object overrides ──────────────────────────────────────────────────────

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AiResponse r)) return false;
        return content.equals(r.content)
                && finishReason == r.finishReason
                && Objects.equals(usage, r.usage)
                && Objects.equals(model, r.model)
                && Objects.equals(id, r.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(content, finishReason, usage, model, id);
    }

    @Override
    public String toString() {
        return "AiResponse{content='" + content + "', finishReason=" + finishReason
                + ", usage=" + usage + '}';
    }
}
