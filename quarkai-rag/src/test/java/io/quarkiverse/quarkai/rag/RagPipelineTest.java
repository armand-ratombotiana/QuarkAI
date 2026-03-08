package io.quarkiverse.quarkai.rag;

import io.quarkiverse.quarkai.core.model.AiRequest;
import io.quarkiverse.quarkai.core.model.AiResponse;
import io.quarkiverse.quarkai.core.model.EmbeddingResult;
import io.quarkiverse.quarkai.core.model.Message;
import io.quarkiverse.quarkai.core.spi.ChatModel;
import io.quarkiverse.quarkai.core.spi.EmbeddingModel;
import io.quarkiverse.quarkai.rag.store.VectorStore;
import io.quarkiverse.quarkai.rag.store.VectorStore.VectorMatch;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link RagPipeline}.
 * Tests the RAG orchestration: embed → search → augment → generate.
 */
@ExtendWith(MockitoExtension.class)
class RagPipelineTest {

    private RagPipeline pipeline;

    @Mock
    private EmbeddingModel mockEmbeddingModel;

    @Mock
    private VectorStore mockVectorStore;

    @Mock
    private ChatModel mockChatModel;

    @BeforeEach
    void setup() {
        pipeline = new RagPipeline();
        pipeline.embeddingModel = mockEmbeddingModel;
        pipeline.vectorStore = mockVectorStore;
        pipeline.chatModel = mockChatModel;
    }

    @Test
    void ask_withMatchingContext_augmentsPrompt() {
        String question = "What is QuarkAI?";
        float[] queryEmbedding = {0.1f, 0.2f, 0.3f};

        // Mock embedding
        EmbeddingResult embResult = EmbeddingResult.of(List.of(queryEmbedding), "test-model", null);
        when(mockEmbeddingModel.embed(List.of(question)))
                .thenReturn(Uni.createFrom().item(embResult));

        // Mock vector search with results
        VectorMatch match1 = new VectorMatch("doc1", "QuarkAI is a reactive AI framework", 0.95f, Map.of());
        VectorMatch match2 = new VectorMatch("doc2", "QuarkAI supports multiple providers", 0.85f, Map.of());
        when(mockVectorStore.search(queryEmbedding, 5, 0.7))
                .thenReturn(Uni.createFrom().item(List.of(match1, match2)));

        // Mock chat model
        AiResponse response = AiResponse.builder()
                .content("QuarkAI is a reactive AI framework that supports multiple providers.")
                .finishReason(AiResponse.FinishReason.STOP)
                .build();
        when(mockChatModel.chat(any())).thenReturn(Uni.createFrom().item(response));

        // Execute
        AiResponse result = pipeline.ask(question)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem(Duration.ofSeconds(2))
                .getItem();

        assertThat(result.content()).isEqualTo("QuarkAI is a reactive AI framework that supports multiple providers.");

        // Verify chat request contains augmented context
        ArgumentCaptor<AiRequest> requestCaptor = ArgumentCaptor.forClass(AiRequest.class);
        verify(mockChatModel).chat(requestCaptor.capture());

        AiRequest capturedRequest = requestCaptor.getValue();
        assertThat(capturedRequest.messages()).hasSize(2);

        // System message should contain context
        Message systemMessage = capturedRequest.messages().get(0);
        assertThat(systemMessage.role()).isEqualTo(Message.Role.SYSTEM);
        assertThat(systemMessage.content()).contains("QuarkAI is a reactive AI framework");
        assertThat(systemMessage.content()).contains("QuarkAI supports multiple providers");

        // User message should be the question
        Message userMessage = capturedRequest.messages().get(1);
        assertThat(userMessage.role()).isEqualTo(Message.Role.USER);
        assertThat(userMessage.content()).isEqualTo(question);
    }

    @Test
    void ask_withNoMatches_fallsBackToGeneralKnowledge() {
        String question = "What is the meaning of life?";
        float[] queryEmbedding = {0.1f, 0.2f, 0.3f};

        // Mock embedding
        EmbeddingResult embResult = EmbeddingResult.of(List.of(queryEmbedding), "test-model", null);
        when(mockEmbeddingModel.embed(List.of(question)))
                .thenReturn(Uni.createFrom().item(embResult));

        // Mock vector search with no results
        when(mockVectorStore.search(queryEmbedding, 5, 0.7))
                .thenReturn(Uni.createFrom().item(List.of()));

        // Mock chat model
        AiResponse response = AiResponse.builder()
                .content("I don't have specific context to answer this question.")
                .finishReason(AiResponse.FinishReason.STOP)
                .build();
        when(mockChatModel.chat(any())).thenReturn(Uni.createFrom().item(response));

        // Execute
        AiResponse result = pipeline.ask(question)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem(Duration.ofSeconds(2))
                .getItem();

        assertThat(result).isNotNull();

        // Verify system message indicates no context found
        ArgumentCaptor<AiRequest> requestCaptor = ArgumentCaptor.forClass(AiRequest.class);
        verify(mockChatModel).chat(requestCaptor.capture());

        Message systemMessage = requestCaptor.getValue().messages().get(0);
        assertThat(systemMessage.content()).contains("No relevant context was found");
        assertThat(systemMessage.content()).contains("general knowledge");
    }

    @Test
    void ask_withCustomParameters_usesSpecifiedTopKAndMinScore() {
        String question = "Test question";
        float[] queryEmbedding = {0.1f, 0.2f};
        int customTopK = 10;
        double customMinScore = 0.9;

        // Mock embedding
        EmbeddingResult embResult = EmbeddingResult.of(List.of(queryEmbedding), "test-model", null);
        when(mockEmbeddingModel.embed(List.of(question)))
                .thenReturn(Uni.createFrom().item(embResult));

        // Mock vector search
        when(mockVectorStore.search(queryEmbedding, customTopK, customMinScore))
                .thenReturn(Uni.createFrom().item(List.of()));

        // Mock chat model
        when(mockChatModel.chat(any()))
                .thenReturn(Uni.createFrom().item(AiResponse.builder()
                        .content("Response")
                        .build()));

        // Execute with custom parameters
        pipeline.ask(question, customTopK, customMinScore)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem(Duration.ofSeconds(2));

        // Verify custom parameters were used
        verify(mockVectorStore).search(queryEmbedding, customTopK, customMinScore);
    }

    @Test
    void withModel_setsCustomModel() {
        String question = "Test";
        String customModel = "gpt-4o";
        float[] queryEmbedding = {0.1f};

        // Mock embedding
        when(mockEmbeddingModel.embed(any()))
                .thenReturn(Uni.createFrom().item(EmbeddingResult.of(List.of(queryEmbedding), "test", null)));

        // Mock vector search
        when(mockVectorStore.search(any(), anyInt(), anyDouble()))
                .thenReturn(Uni.createFrom().item(List.of()));

        // Mock chat model
        when(mockChatModel.chat(any()))
                .thenReturn(Uni.createFrom().item(AiResponse.builder().content("Response").build()));

        // Execute with custom model
        pipeline.withModel(customModel)
                .ask(question)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem(Duration.ofSeconds(2));

        // Verify model was set
        ArgumentCaptor<AiRequest> requestCaptor = ArgumentCaptor.forClass(AiRequest.class);
        verify(mockChatModel).chat(requestCaptor.capture());
        assertThat(requestCaptor.getValue().model()).isEqualTo(customModel);
    }

    @Test
    void ask_orchestratesFullPipeline() {
        String question = "How does RAG work?";
        float[] queryEmbedding = {0.5f, 0.5f};

        // Mock each stage
        when(mockEmbeddingModel.embed(List.of(question)))
                .thenReturn(Uni.createFrom().item(
                        EmbeddingResult.of(List.of(queryEmbedding), "test-model", null)));

        VectorMatch match = new VectorMatch("doc1", "RAG combines retrieval with generation", 0.9f, Map.of());
        when(mockVectorStore.search(queryEmbedding, 5, 0.7))
                .thenReturn(Uni.createFrom().item(List.of(match)));

        when(mockChatModel.chat(any()))
                .thenReturn(Uni.createFrom().item(
                        AiResponse.builder()
                                .content("RAG works by retrieving relevant context and generating answers.")
                                .finishReason(AiResponse.FinishReason.STOP)
                                .build()));

        // Execute
        AiResponse result = pipeline.ask(question)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem(Duration.ofSeconds(2))
                .getItem();

        // Verify all stages were called in order
        verify(mockEmbeddingModel).embed(List.of(question));
        verify(mockVectorStore).search(queryEmbedding, 5, 0.7);
        verify(mockChatModel).chat(any());

        assertThat(result.content()).contains("RAG works by");
    }

    @Test
    void ask_embeddingFailure_propagatesError() {
        when(mockEmbeddingModel.embed(any()))
                .thenReturn(Uni.createFrom().failure(new RuntimeException("Embedding service unavailable")));

        Throwable failure = pipeline.ask("Test question")
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitFailure()
                .getFailure();

        assertThat(failure)
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Embedding service unavailable");

        verify(mockVectorStore, never()).search(any(), anyInt(), anyDouble());
        verify(mockChatModel, never()).chat(any());
    }

    @Test
    void ask_vectorStoreFailure_propagatesError() {
        float[] embedding = {0.1f};
        when(mockEmbeddingModel.embed(any()))
                .thenReturn(Uni.createFrom().item(EmbeddingResult.of(List.of(embedding), "test", null)));

        when(mockVectorStore.search(any(), anyInt(), anyDouble()))
                .thenReturn(Uni.createFrom().failure(new RuntimeException("Vector store connection failed")));

        Throwable failure = pipeline.ask("Test question")
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitFailure()
                .getFailure();

        assertThat(failure)
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Vector store connection failed");

        verify(mockChatModel, never()).chat(any());
    }
}
