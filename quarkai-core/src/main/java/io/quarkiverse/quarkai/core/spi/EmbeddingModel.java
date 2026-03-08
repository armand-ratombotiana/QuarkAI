package io.quarkiverse.quarkai.core.spi;

import io.quarkiverse.quarkai.core.model.EmbeddingResult;
import io.smallrye.mutiny.Uni;

import java.util.List;

/**
 * Model interface for generating text embeddings (vector representations).
 *
 * <p>Usage:
 * <pre>{@code
 * @Inject EmbeddingModel embeddingModel;
 *
 * embeddingModel.embed(List.of("Hello world", "Another sentence"))
 *     .subscribe().with(result -> storeInVectorDb(result.embeddings()));
 * }</pre>
 */
public interface EmbeddingModel extends AiModel {

    /**
     * Generates embeddings for the given list of text inputs.
     *
     * @param texts non-null, non-empty list of input strings
     * @return a {@link Uni} wrapping an {@link EmbeddingResult}
     */
    Uni<EmbeddingResult> embed(List<String> texts);
}
