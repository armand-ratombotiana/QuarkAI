package io.quarkiverse.quarkai.rag.store;

import io.smallrye.mutiny.Uni;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe, in-memory implementation of {@link VectorStore}.
 *
 * <p>Uses cosine similarity for ranking. Suitable for development, testing,
 * and small-scale deployments. Not persistent across restarts.
 */
public class InMemoryVectorStore implements VectorStore {

    private record Entry(String id, float[] vector, String text, Map<String, String> metadata) {}

    private final Map<String, Entry> store = new ConcurrentHashMap<>();

    @Override
    public Uni<Void> add(String id, float[] vector, String text, Map<String, String> metadata) {
        store.put(id, new Entry(id, vector.clone(), text,
                metadata != null ? Map.copyOf(metadata) : Map.of()));
        return Uni.createFrom().voidItem();
    }

    @Override
    public Uni<List<VectorMatch>> search(float[] queryVector, int topK, double minScore) {
        List<VectorMatch> results = new ArrayList<>();

        for (Entry entry : store.values()) {
            float score = cosineSimilarity(queryVector, entry.vector());
            if (score >= minScore) {
                results.add(new VectorMatch(entry.id(), entry.text(), score, entry.metadata()));
            }
        }

        results.sort(Comparator.comparingDouble(VectorMatch::score).reversed());
        List<VectorMatch> topResults = results.stream().limit(topK).toList();
        return Uni.createFrom().item(topResults);
    }

    @Override
    public Uni<Void> delete(String id) {
        store.remove(id);
        return Uni.createFrom().voidItem();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    public int size() {
        return store.size();
    }

    /**
     * Cosine similarity between two float vectors (range: -1.0 to 1.0).
     * Returns 0.0 if either vector has zero magnitude.
     */
    static float cosineSimilarity(float[] a, float[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException(
                    "Vector dimension mismatch: " + a.length + " vs " + b.length);
        }
        double dot = 0.0, normA = 0.0, normB = 0.0;
        for (int i = 0; i < a.length; i++) {
            dot   += (double) a[i] * b[i];
            normA += (double) a[i] * a[i];
            normB += (double) b[i] * b[i];
        }
        double denom = Math.sqrt(normA) * Math.sqrt(normB);
        return denom == 0.0 ? 0.0f : (float) (dot / denom);
    }
}
