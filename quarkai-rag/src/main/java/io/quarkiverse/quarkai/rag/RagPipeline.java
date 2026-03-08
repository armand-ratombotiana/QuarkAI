package io.quarkiverse.quarkai.rag;

import io.quarkiverse.quarkai.core.model.AiRequest;
import io.quarkiverse.quarkai.core.model.AiResponse;
import io.quarkiverse.quarkai.core.model.Message;
import io.quarkiverse.quarkai.core.spi.ChatModel;
import io.quarkiverse.quarkai.core.spi.EmbeddingModel;
import io.quarkiverse.quarkai.rag.store.VectorStore;
import io.quarkiverse.quarkai.rag.store.VectorStore.VectorMatch;
import io.smallrye.mutiny.Uni;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Retrieval-Augmented Generation pipeline.
 *
 * <p>Orchestrates:
 * <ol>
 *   <li>Embed the user query using {@link EmbeddingModel}
 *   <li>Retrieve top-k similar documents from {@link VectorStore}
 *   <li>Build an augmented prompt injecting retrieved context
 *   <li>Call {@link ChatModel} with the augmented prompt
 * </ol>
 *
 * <p>Usage:
 * <pre>{@code
 * @Inject RagPipeline rag;
 *
 * rag.ask("What is QuarkAI?")
 *    .subscribe().with(response -> System.out.println(response.content()));
 * }</pre>
 */
@ApplicationScoped
public class RagPipeline {

    private static final int    DEFAULT_TOP_K     = 5;
    private static final double DEFAULT_MIN_SCORE = 0.7;

    @Inject EmbeddingModel embeddingModel;
    @Inject VectorStore    vectorStore;
    @Inject ChatModel      chatModel;

    /**
     * Answers a question using context retrieved from the vector store.
     *
     * @param question  the user's natural-language question
     * @return a {@link Uni} with the AI-generated answer
     */
    public Uni<AiResponse> ask(String question) {
        return ask(question, DEFAULT_TOP_K, DEFAULT_MIN_SCORE);
    }

    /**
     * Answers with configurable retrieval parameters.
     */
    public Uni<AiResponse> ask(String question, int topK, double minScore) {
        return embeddingModel.embed(List.of(question))
                .flatMap(embResult -> {
                    float[] queryVec = embResult.firstEmbedding();
                    return vectorStore.search(queryVec, topK, minScore);
                })
                .flatMap(matches -> {
                    AiRequest request = buildAugmentedRequest(question, matches);
                    return chatModel.chat(request);
                });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Override the model used for the generation step.
     * If null/empty, the injected {@link ChatModel}'s own configured default is used.
     */
    private String generationModel;

    public RagPipeline withModel(String model) {
        this.generationModel = model;
        return this;
    }

    private AiRequest buildAugmentedRequest(String question, List<VectorMatch> matches) {
        List<Message> messages = new ArrayList<>();

        // System prompt with retrieved context
        if (!matches.isEmpty()) {
            String context = matches.stream()
                    .map(m -> "- " + m.text())
                    .collect(Collectors.joining("\n"));
            messages.add(Message.system(
                    "You are a helpful assistant. Use the following retrieved context to answer " +
                    "the question accurately. If the context doesn't contain the answer, " +
                    "say so honestly.\n\nContext:\n" + context));
        } else {
            messages.add(Message.system(
                    "You are a helpful assistant. No relevant context was found; " +
                    "answer based on your general knowledge."));
        }

        messages.add(Message.user(question));

        // Use explicitly set model, or fall back to a sensible default.
        // The ChatModel implementation will apply its own config if the model matches.
        String model = (generationModel != null && !generationModel.isBlank())
                ? generationModel
                : "default"; // provider-side default; won't cause NPE in AiRequest validator

        return AiRequest.builder()
                .model(model)
                .messages(messages)
                .build();
    }
}
