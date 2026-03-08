package io.quarkiverse.quarkai.quarkus.runtime;

import io.quarkiverse.quarkai.anthropic.AnthropicChatModel;
import io.quarkiverse.quarkai.core.spi.ChatModel;
import io.quarkiverse.quarkai.core.spi.EmbeddingModel;
import io.quarkiverse.quarkai.core.spi.StreamingChatModel;
import io.quarkiverse.quarkai.ollama.OllamaChatModel;
import io.quarkiverse.quarkai.openai.OpenAiChatModel;
import io.quarkiverse.quarkai.openai.OpenAiEmbeddingModel;
import io.quarkiverse.quarkai.quarkus.runtime.config.QuarkAiConfig;
import io.quarkiverse.quarkai.vertex.VertexAiChatModel;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

/**
 * CDI producer that selects and exposes the appropriate {@link ChatModel},
 * {@link StreamingChatModel}, and {@link EmbeddingModel} beans based on
 * {@code quarkai.provider} configuration.
 *
 * <p>This is the central injection point that lets application code write:
 * <pre>{@code
 * @Inject ChatModel chatModel;
 * @Inject StreamingChatModel streamingModel;
 * }</pre>
 * and switch providers with a single property change.
 */
@ApplicationScoped
public class QuarkAiProducers {

    @Inject QuarkAiConfig config;

    @Inject Instance<OpenAiChatModel>    openAiChatModel;
    @Inject Instance<AnthropicChatModel> anthropicChatModel;
    @Inject Instance<VertexAiChatModel>  vertexAiChatModel;
    @Inject Instance<OllamaChatModel>    ollamaChatModel;
    @Inject Instance<OpenAiEmbeddingModel> openAiEmbeddingModel;

    @Produces
    @ApplicationScoped
    public ChatModel chatModel() {
        return switch (config.provider().toLowerCase()) {
            case "anthropic" -> anthropicChatModel.get();
            case "vertex"    -> vertexAiChatModel.get();
            case "ollama"    -> ollamaChatModel.get();
            default          -> openAiChatModel.get();  // "openai" or unrecognized
        };
    }

    @Produces
    @ApplicationScoped
    public StreamingChatModel streamingChatModel() {
        return switch (config.provider().toLowerCase()) {
            case "anthropic" -> anthropicChatModel.get();
            case "vertex"    -> vertexAiChatModel.get();
            case "ollama"    -> ollamaChatModel.get();
            default          -> openAiChatModel.get();
        };
    }

    @Produces
    @ApplicationScoped
    public EmbeddingModel embeddingModel() {
        // Currently only OpenAI implements EmbeddingModel; extend as needed
        return openAiEmbeddingModel.get();
    }
}
