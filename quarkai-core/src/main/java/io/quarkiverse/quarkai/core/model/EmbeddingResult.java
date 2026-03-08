package io.quarkiverse.quarkai.core.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Immutable result of a text embedding call, containing one float vector per input text.
 */
public final class EmbeddingResult {

    private final List<float[]> embeddings;
    private final String model;
    private final TokenUsage usage;

    private EmbeddingResult(List<float[]> embeddings, String model, TokenUsage usage) {
        Objects.requireNonNull(embeddings, "embeddings must not be null");
        // Defensive copy list (individual float[] arrays are provider-owned)
        this.embeddings = Collections.unmodifiableList(new ArrayList<>(embeddings));
        this.model      = model;
        this.usage      = usage;
    }

    // ── Static factories ──────────────────────────────────────────────────────

    public static EmbeddingResult of(List<float[]> embeddings, String model, TokenUsage usage) {
        return new EmbeddingResult(embeddings, model, usage);
    }

    public static EmbeddingResult of(List<float[]> embeddings) {
        return new EmbeddingResult(embeddings, null, null);
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    /** One float vector per input text. The order matches the input order. */
    public List<float[]> embeddings() {
        return embeddings;
    }

    /** Convenience: returns the single embedding when exactly one input was provided. */
    public float[] firstEmbedding() {
        if (embeddings.isEmpty()) {
            throw new IllegalStateException("EmbeddingResult is empty");
        }
        return embeddings.get(0);
    }

    /** The dimension of each vector. */
    public int dimension() {
        return embeddings.isEmpty() ? 0 : embeddings.get(0).length;
    }

    /** Number of input texts that were embedded. */
    public int count() {
        return embeddings.size();
    }

    public java.util.Optional<String> model() {
        return java.util.Optional.ofNullable(model);
    }

    public java.util.Optional<TokenUsage> usage() {
        return java.util.Optional.ofNullable(usage);
    }

    // ── Object overrides ──────────────────────────────────────────────────────

    @Override
    public String toString() {
        return "EmbeddingResult{count=" + count() + ", dimension=" + dimension()
                + ", model=" + model + '}';
    }
}
