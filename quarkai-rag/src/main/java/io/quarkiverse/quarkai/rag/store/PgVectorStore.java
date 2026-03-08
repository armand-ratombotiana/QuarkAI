package io.quarkiverse.quarkai.rag.store;

import io.quarkus.reactive.datasource.ReactiveDataSource;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.pgclient.PgPool;
import io.vertx.mutiny.sqlclient.Tuple;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * PostgreSQL pgvector-backed {@link VectorStore}.
 *
 * <p>Requires the {@code pgvector} extension enabled in your database and the
 * following table:
 * <pre>{@code
 * CREATE EXTENSION IF NOT EXISTS vector;
 * CREATE TABLE IF NOT EXISTS quarkai_embeddings (
 *     id      TEXT PRIMARY KEY,
 *     text    TEXT NOT NULL,
 *     vector  vector(1536),
 *     metadata JSONB DEFAULT '{}'
 * );
 * CREATE INDEX ON quarkai_embeddings USING ivfflat (vector vector_cosine_ops);
 * }</pre>
 *
 * <p>Configure via standard Quarkus reactive datasource properties.
 */
@ApplicationScoped
public class PgVectorStore implements VectorStore {

    @Inject
    @ReactiveDataSource("quarkai")
    PgPool client;

    @Override
    public Uni<Void> add(String id, float[] vector, String text, Map<String, String> metadata) {
        String vectorLiteral = toVectorLiteral(vector);
        String metaJson      = metadataToJson(metadata);

        return client.preparedQuery(
                "INSERT INTO quarkai_embeddings (id, text, vector, metadata) " +
                "VALUES ($1, $2, $3::vector, $4::jsonb) " +
                "ON CONFLICT (id) DO UPDATE SET text=EXCLUDED.text, vector=EXCLUDED.vector, metadata=EXCLUDED.metadata")
                .execute(Tuple.of(id, text, vectorLiteral, metaJson))
                .replaceWithVoid();
    }

    @Override
    public Uni<List<VectorMatch>> search(float[] queryVector, int topK, double minScore) {
        String vectorLiteral = toVectorLiteral(queryVector);

        return client.preparedQuery(
                "SELECT id, text, metadata, 1 - (vector <=> $1::vector) AS score " +
                "FROM quarkai_embeddings " +
                "WHERE 1 - (vector <=> $1::vector) >= $2 " +
                "ORDER BY score DESC LIMIT $3")
                .execute(Tuple.of(vectorLiteral, minScore, topK))
                .map(rows -> {
                    List<VectorMatch> results = new ArrayList<>();
                    for (var row : rows) {
                        results.add(new VectorMatch(
                                row.getString("id"),
                                row.getString("text"),
                                row.getFloat("score"),
                                Map.of()  // metadata deserialization omitted for brevity
                        ));
                    }
                    return results;
                });
    }

    @Override
    public Uni<Void> delete(String id) {
        return client.preparedQuery("DELETE FROM quarkai_embeddings WHERE id = $1")
                .execute(Tuple.of(id))
                .replaceWithVoid();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String toVectorLiteral(float[] vector) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(vector[i]);
        }
        sb.append(']');
        return sb.toString();
    }

    private static String metadataToJson(Map<String, String> metadata) {
        if (metadata == null || metadata.isEmpty()) return "{}";
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (var entry : metadata.entrySet()) {
            if (!first) sb.append(',');
            sb.append('"').append(entry.getKey()).append("\":\"").append(entry.getValue()).append('"');
            first = false;
        }
        sb.append('}');
        return sb.toString();
    }
}
