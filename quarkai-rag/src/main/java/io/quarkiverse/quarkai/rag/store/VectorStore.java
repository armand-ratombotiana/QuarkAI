package io.quarkiverse.quarkai.rag.store;

import io.smallrye.mutiny.Uni;

import java.util.List;

/**
 * Provider-agnostic vector store interface for storing and querying embedding vectors.
 *
 * <p>Implementations: {@link InMemoryVectorStore}, {@code PgVectorStore}.
 */
public interface VectorStore {

    /**
     * Adds an embedding document to the store.
     *
     * @param id        unique document identifier
     * @param vector    the embedding vector
     * @param text      original text for retrieval
     * @param metadata  optional key-value metadata
     * @return a {@link Uni} that completes when the document is persisted
     */
    Uni<Void> add(String id, float[] vector, String text, java.util.Map<String, String> metadata);

    /**
     * Finds the top-k most similar documents to the given query vector.
     *
     * @param queryVector the embedding of the query
     * @param topK        number of results to return
     * @param minScore    minimum cosine similarity threshold (0.0–1.0)
     * @return a {@link Uni} emitting a ranked list of {@link VectorMatch}
     */
    Uni<List<VectorMatch>> search(float[] queryVector, int topK, double minScore);

    /**
     * Deletes a document by ID.
     *
     * @param id document identifier
     * @return a {@link Uni} that completes when deleted (silently succeeds if not found)
     */
    Uni<Void> delete(String id);

    /**
     * A single vector search result.
     */
    record VectorMatch(String id, String text, float score, java.util.Map<String, String> metadata) {}
}
