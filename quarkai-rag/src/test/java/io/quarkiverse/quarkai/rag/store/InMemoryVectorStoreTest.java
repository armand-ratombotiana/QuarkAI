package io.quarkiverse.quarkai.rag.store;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class InMemoryVectorStoreTest {

    private InMemoryVectorStore store;

    @BeforeEach
    void setUp() {
        store = new InMemoryVectorStore();
    }

    // ── add / size ─────────────────────────────────────────────────────────────

    @Test
    void add_increasesSize() {
        store.add("doc1", new float[]{1f, 0f, 0f}, "First doc", Map.of()).await().indefinitely();
        assertThat(store.size()).isEqualTo(1);
    }

    @Test
    void add_overwritesExistingId() {
        store.add("doc1", new float[]{1f, 0f, 0f}, "Original", Map.of()).await().indefinitely();
        store.add("doc1", new float[]{0f, 1f, 0f}, "Updated",  Map.of()).await().indefinitely();
        assertThat(store.size()).isEqualTo(1);

        List<VectorStore.VectorMatch> results =
                store.search(new float[]{0f, 1f, 0f}, 1, 0.9).await().indefinitely();
        assertThat(results).hasSize(1);
        assertThat(results.get(0).text()).isEqualTo("Updated");
    }

    // ── delete ────────────────────────────────────────────────────────────────

    @Test
    void delete_removesEntry() {
        store.add("doc1", new float[]{1f, 0f}, "Hello", Map.of()).await().indefinitely();
        store.delete("doc1").await().indefinitely();
        assertThat(store.size()).isZero();
    }

    @Test
    void delete_silentlyIgnoresMissingId() {
        assertThatCode(() -> store.delete("nonexistent").await().indefinitely())
                .doesNotThrowAnyException();
    }

    // ── search ────────────────────────────────────────────────────────────────

    @Test
    void search_returnsTopKByCosineSimilarity() {
        store.add("doc1", new float[]{1f, 0f}, "A", Map.of()).await().indefinitely();
        store.add("doc2", new float[]{0f, 1f}, "B", Map.of()).await().indefinitely();
        store.add("doc3", new float[]{1f, 1f}, "C", Map.of()).await().indefinitely(); // 45° from both axes

        // Query aligned with doc1
        List<VectorStore.VectorMatch> results =
                store.search(new float[]{1f, 0f}, 2, 0.0).await().indefinitely();

        assertThat(results).hasSize(2);
        assertThat(results.get(0).id()).isEqualTo("doc1"); // score=1.0
    }

    @Test
    void search_respectsMinScoreFilter() {
        store.add("doc1", new float[]{1f, 0f}, "A", Map.of()).await().indefinitely();
        store.add("doc2", new float[]{0f, 1f}, "B", Map.of()).await().indefinitely();

        // Query aligned with doc1; doc2 has cosine = 0.0 → filtered out by minScore=0.5
        List<VectorStore.VectorMatch> results =
                store.search(new float[]{1f, 0f}, 5, 0.5).await().indefinitely();

        assertThat(results).hasSize(1);
        assertThat(results.get(0).id()).isEqualTo("doc1");
    }

    @Test
    void search_emptyStoreReturnsEmpty() {
        List<VectorStore.VectorMatch> results =
                store.search(new float[]{1f, 0f}, 5, 0.0).await().indefinitely();
        assertThat(results).isEmpty();
    }

    @Test
    void search_topKLimitsResults() {
        for (int i = 0; i < 10; i++) {
            store.add("doc" + i, new float[]{1f, (float) i}, "Text " + i, Map.of())
                    .await().indefinitely();
        }
        List<VectorStore.VectorMatch> results =
                store.search(new float[]{1f, 0f}, 3, 0.0).await().indefinitely();
        assertThat(results).hasSize(3);
    }

    @Test
    void search_scoresAreDescending() {
        store.add("exact",  new float[]{1f, 0f}, "Exact",  Map.of()).await().indefinitely();
        store.add("close",  new float[]{0.9f, 0.1f}, "Close",  Map.of()).await().indefinitely();
        store.add("far",    new float[]{0f, 1f}, "Far",    Map.of()).await().indefinitely();

        List<VectorStore.VectorMatch> results =
                store.search(new float[]{1f, 0f}, 3, 0.0).await().indefinitely();

        assertThat(results.get(0).score()).isGreaterThanOrEqualTo(results.get(1).score());
        assertThat(results.get(1).score()).isGreaterThanOrEqualTo(results.get(2).score());
    }

    @Test
    void search_metadataIsPreserved() {
        Map<String, String> meta = Map.of("source", "wiki", "lang", "en");
        store.add("doc1", new float[]{1f, 0f}, "Hello", meta).await().indefinitely();

        List<VectorStore.VectorMatch> results =
                store.search(new float[]{1f, 0f}, 1, 0.9).await().indefinitely();

        assertThat(results.get(0).metadata()).containsEntry("source", "wiki");
    }

    // ── cosineSimilarity ──────────────────────────────────────────────────────

    @Test
    void cosineSimilarity_identicalVectors_returnsOne() {
        float[] v = {1f, 2f, 3f};
        assertThat(InMemoryVectorStore.cosineSimilarity(v, v)).isCloseTo(1.0f, offset(0.0001f));
    }

    @Test
    void cosineSimilarity_orthogonalVectors_returnsZero() {
        assertThat(InMemoryVectorStore.cosineSimilarity(
                new float[]{1f, 0f}, new float[]{0f, 1f}))
                .isCloseTo(0.0f, offset(0.0001f));
    }

    @Test
    void cosineSimilarity_dimensionMismatch_throws() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> InMemoryVectorStore.cosineSimilarity(
                        new float[]{1f, 2f}, new float[]{1f}));
    }

    @Test
    void cosineSimilarity_zeroVector_returnsZero() {
        assertThat(InMemoryVectorStore.cosineSimilarity(
                new float[]{0f, 0f}, new float[]{1f, 1f}))
                .isCloseTo(0.0f, offset(0.0001f));
    }
}
