package io.quarkiverse.quarkai.example;

import io.quarkiverse.quarkai.core.model.AiRequest;
import io.quarkiverse.quarkai.core.model.AiResponse;
import io.quarkiverse.quarkai.core.model.Message;
import io.quarkiverse.quarkai.core.spi.ChatModel;
import io.quarkiverse.quarkai.core.spi.StreamingChatModel;
import io.quarkiverse.quarkai.rag.RagPipeline;
import io.quarkiverse.quarkai.vertx.AiCircuitBreaker;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;

/**
 * REST API demonstrating QuarkAI features.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>POST /api/chat — single chat completion
 *   <li>POST /api/chat/stream — streaming chat completion (SSE)
 *   <li>POST /api/chat/protected — circuit breaker protected request
 *   <li>POST /api/rag/ask — RAG-based question answering
 * </ul>
 */
@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AiChatResource {

    @Inject
    ChatModel chatModel;

    @Inject
    StreamingChatModel streamingChatModel;

    @Inject
    AiCircuitBreaker circuitBreaker;

    @Inject
    RagPipeline ragPipeline;

    /**
     * Single chat completion.
     *
     * <p>Example request:
     * <pre>{@code
     * POST /api/chat
     * {
     *   "model": "gpt-4o",
     *   "messages": [
     *     {"role": "user", "content": "What is QuarkAI?"}
     *   ]
     * }
     * }</pre>
     */
    @POST
    @Path("/chat")
    public Uni<AiResponse> chat(ChatRequest request) {
        AiRequest aiRequest = AiRequest.builder()
                .model(request.model)
                .messages(request.messages.stream()
                        .map(msg -> Message.of(
                                Message.Role.valueOf(msg.role.toUpperCase()),
                                msg.content))
                        .toList())
                .temperature(request.temperature != null ? request.temperature : 0.7)
                .maxTokens(request.maxTokens != null ? request.maxTokens : 2000)
                .build();

        return chatModel.chat(aiRequest);
    }

    /**
     * Streaming chat completion (Server-Sent Events).
     *
     * <p>Example:
     * <pre>{@code
     * POST /api/chat/stream
     * Accept: text/event-stream
     * }</pre>
     */
    @POST
    @Path("/chat/stream")
    @Produces(MediaType.SERVER_SENT_EVENTS)
    public Multi<String> chatStream(ChatRequest request) {
        AiRequest aiRequest = AiRequest.builder()
                .model(request.model)
                .messages(request.messages.stream()
                        .map(msg -> Message.of(
                                Message.Role.valueOf(msg.role.toUpperCase()),
                                msg.content))
                        .toList())
                .build();

        return streamingChatModel.stream(aiRequest)
                .map(chunk -> "data: " + chunk.content() + "\n\n");
    }

    /**
     * Circuit breaker protected chat request.
     *
     * <p>Fails fast when the AI provider is unavailable.
     */
    @POST
    @Path("/chat/protected")
    public Uni<AiResponse> chatWithCircuitBreaker(ChatRequest request) {
        AiRequest aiRequest = AiRequest.builder()
                .model(request.model)
                .messages(request.messages.stream()
                        .map(msg -> Message.of(
                                Message.Role.valueOf(msg.role.toUpperCase()),
                                msg.content))
                        .toList())
                .build();

        return circuitBreaker.chat(aiRequest);
    }

    /**
     * RAG-based question answering.
     *
     * <p>Example:
     * <pre>{@code
     * POST /api/rag/ask
     * {
     *   "question": "What is QuarkAI?",
     *   "topK": 5,
     *   "minScore": 0.7
     * }
     * }</pre>
     */
    @POST
    @Path("/rag/ask")
    public Uni<AiResponse> ragAsk(RagRequest request) {
        return ragPipeline.ask(
                request.question,
                request.topK != null ? request.topK : 5,
                request.minScore != null ? request.minScore : 0.7);
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Request/Response DTOs
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    public static class ChatRequest {
        public String model;
        public java.util.List<MessageDTO> messages;
        public Double temperature;
        public Integer maxTokens;
    }

    public static class MessageDTO {
        public String role;
        public String content;
    }

    public static class RagRequest {
        public String question;
        public Integer topK;
        public Double minScore;
    }
}
