package io.quarkiverse.quarkai.core.model;

import java.util.Objects;

/**
 * Immutable record of token consumption for one AI model call.
 */
public final class TokenUsage {

    private final int promptTokens;
    private final int completionTokens;
    private final int totalTokens;

    private TokenUsage(int promptTokens, int completionTokens, int totalTokens) {
        if (promptTokens < 0 || completionTokens < 0) {
            throw new IllegalArgumentException(
                    "Token counts must be non-negative. Got prompt=" + promptTokens
                            + ", completion=" + completionTokens);
        }
        this.promptTokens     = promptTokens;
        this.completionTokens = completionTokens;
        // Allow explicit total override (some APIs omit per-type breakdown)
        this.totalTokens      = totalTokens >= 0 ? totalTokens : (promptTokens + completionTokens);
    }

    // ── Static factories ──────────────────────────────────────────────────────

    /**
     * Creates a {@code TokenUsage} where total = prompt + completion.
     */
    public static TokenUsage of(int promptTokens, int completionTokens) {
        return new TokenUsage(promptTokens, completionTokens, -1);
    }

    /**
     * Creates a {@code TokenUsage} with an explicit total (e.g. when the provider
     * gives cached-token breakdowns that don't sum simply).
     */
    public static TokenUsage of(int promptTokens, int completionTokens, int totalTokens) {
        return new TokenUsage(promptTokens, completionTokens, totalTokens);
    }

    /** Zero-usage sentinel (useful for stream chunks where usage is not yet known). */
    public static TokenUsage zero() {
        return new TokenUsage(0, 0, 0);
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public int promptTokens() {
        return promptTokens;
    }

    public int completionTokens() {
        return completionTokens;
    }

    public int totalTokens() {
        return totalTokens;
    }

    // ── Object overrides ──────────────────────────────────────────────────────

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TokenUsage t)) return false;
        return promptTokens == t.promptTokens
                && completionTokens == t.completionTokens
                && totalTokens == t.totalTokens;
    }

    @Override
    public int hashCode() {
        return Objects.hash(promptTokens, completionTokens, totalTokens);
    }

    @Override
    public String toString() {
        return "TokenUsage{prompt=" + promptTokens + ", completion=" + completionTokens
                + ", total=" + totalTokens + '}';
    }
}
