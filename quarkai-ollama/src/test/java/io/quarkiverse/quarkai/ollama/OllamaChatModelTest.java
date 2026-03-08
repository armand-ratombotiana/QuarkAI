package io.quarkiverse.quarkai.ollama;

import io.quarkiverse.quarkai.core.exception.AiException;
import io.quarkiverse.quarkai.core.model.AiRequest;
import io.quarkiverse.quarkai.core.model.AiResponse;
import io.quarkiverse.quarkai.core.model.Message;
import io.quarkiverse.quarkai.ollama.config.OllamaConfig;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Integration tests for {@link OllamaChatModel}.
 *
 * Tests Ollama-specific protocol details:
 * - OpenAI-compatible messages format
 * - NDJSON (newline-delimited JSON) streaming
 * - Local API without authentication
 * - "options" field for temperature and max tokens
 */
@ExtendWith(MockitoExtension.class)
class OllamaChatModelTest {

    private static final int MOCK_PORT = 18083;
    private static final String MOCK_BASE_URL = "http://localhost:" + MOCK_PORT;

    private Vertx vertx;
    private HttpServer mockServer;
    private OllamaChatModel chatModel;

    @Mock
    private OllamaConfig mockConfig;

    @BeforeEach
    void setup() {
        vertx = Vertx.vertx();

        when(mockConfig.baseUrl()).thenReturn(MOCK_BASE_URL);
        when(mockConfig.timeoutSeconds()).thenReturn(30L);

        chatModel = new OllamaChatModel();
        chatModel.config = mockConfig;
        chatModel.vertx = vertx;
        chatModel.init();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (chatModel != null) {
            chatModel.destroy();
        }
        if (mockServer != null) {
            CountDownLatch latch = new CountDownLatch(1);
            mockServer.close(ar -> latch.countDown());
            latch.await(5, TimeUnit.SECONDS);
        }
        if (vertx != null) {
            CountDownLatch latch = new CountDownLatch(1);
            vertx.close(ar -> latch.countDown());
            latch.await(5, TimeUnit.SECONDS);
        }
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Successful Chat (Non-Streaming) Tests
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Test
    void chat_successfulResponse_returnsAiResponse() throws Exception {
        String mockResponse = """
                {
                  "model": "llama3.2",
                  "message": {
                    "role": "assistant",
                    "content": "Paris is the capital of France."
                  },
                  "done": true,
                  "prompt_eval_count": 18,
                  "eval_count": 9
                }
                """;

        startMockServer((router) -> {
            router.post("/api/chat").handler(ctx -> {
                // Ollama has no authentication headers
                assertThat(ctx.request().getHeader("Content-Type")).isEqualTo("application/json");

                ctx.response()
                        .setStatusCode(200)
                        .putHeader("Content-Type", "application/json")
                        .end(mockResponse);
            });
        });

        AiRequest request = AiRequest.builder()
                .model("llama3.2")
                .addMessage(Message.user("What is the capital of France?"))
                .build();

        AiResponse response = chatModel.chat(request)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem(Duration.ofSeconds(5))
                .getItem();

        assertThat(response).isNotNull();
        assertThat(response.content()).isEqualTo("Paris is the capital of France.");
        assertThat(response.finishReason()).isEqualTo(AiResponse.FinishReason.STOP);
        assertThat(response.model()).contains("llama3.2");
        assertThat(response.usage()).isPresent();
        assertThat(response.usage().get().promptTokens()).isEqualTo(18);
        assertThat(response.usage().get().completionTokens()).isEqualTo(9);
    }

    @Test
    void chat_withSystemMessage_includesInMessages() throws Exception {
        AtomicInteger callCount = new AtomicInteger(0);

        startMockServer((router) -> {
            router.post("/api/chat").handler(ctx -> {
                callCount.incrementAndGet();
                String body = ctx.body().asString();

                // Ollama supports system messages in the messages array
                assertThat(body).contains("\"role\":\"system\"");
                assertThat(body).contains("You are a helpful assistant");
                assertThat(body).contains("\"role\":\"user\"");

                ctx.response()
                        .setStatusCode(200)
                        .putHeader("Content-Type", "application/json")
                        .end("{\"message\":{\"content\":\"OK\"},\"done\":true,\"prompt_eval_count\":10,\"eval_count\":2}");
            });
        });

        AiRequest request = AiRequest.builder()
                .model("llama3.2")
                .addMessage(Message.system("You are a helpful assistant"))
                .addMessage(Message.user("Hello"))
                .build();

        chatModel.chat(request)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem(Duration.ofSeconds(5));

        assertThat(callCount.get()).isEqualTo(1);
    }

    @Test
    void chat_withOptions_sendsTemperatureAndMaxTokens() throws Exception {
        AtomicInteger callCount = new AtomicInteger(0);

        startMockServer((router) -> {
            router.post("/api/chat").handler(ctx -> {
                callCount.incrementAndGet();
                String body = ctx.body().asString();

                // Verify options field
                assertThat(body).contains("\"options\"");
                assertThat(body).contains("\"temperature\":0.8");
                assertThat(body).contains("\"num_predict\":512");

                ctx.response()
                        .setStatusCode(200)
                        .putHeader("Content-Type", "application/json")
                        .end("{\"message\":{\"content\":\"OK\"},\"done\":true,\"prompt_eval_count\":10,\"eval_count\":2}");
            });
        });

        AiRequest request = AiRequest.builder()
                .model("llama3.2")
                .addMessage(Message.user("Test"))
                .temperature(0.8)
                .maxTokens(512)
                .build();

        chatModel.chat(request)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem(Duration.ofSeconds(5));

        assertThat(callCount.get()).isEqualTo(1);
    }

    @Test
    void chat_doneTrue_mapsToStopFinishReason() throws Exception {
        String mockResponse = """
                {
                  "message": {"content": "Response"},
                  "done": true,
                  "prompt_eval_count": 5,
                  "eval_count": 3
                }
                """;

        startMockServer((router) -> {
            router.post("/api/chat").handler(ctx -> {
                ctx.response()
                        .setStatusCode(200)
                        .putHeader("Content-Type", "application/json")
                        .end(mockResponse);
            });
        });

        AiRequest request = AiRequest.builder()
                .model("llama3.2")
                .addMessage(Message.user("Test"))
                .build();

        AiResponse response = chatModel.chat(request)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem(Duration.ofSeconds(5))
                .getItem();

        assertThat(response.finishReason()).isEqualTo(AiResponse.FinishReason.STOP);
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Error Handling Tests (Non-Streaming)
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Test
    void chat_serverError_throwsAiException() throws Exception {
        startMockServer((router) -> {
            router.post("/api/chat").handler(ctx -> {
                ctx.response()
                        .setStatusCode(500)
                        .putHeader("Content-Type", "application/json")
                        .end("{\"error\":\"Internal server error\"}");
            });
        });

        AiRequest request = AiRequest.builder()
                .model("llama3.2")
                .addMessage(Message.user("Test"))
                .build();

        Throwable failure = chatModel.chat(request)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitFailure()
                .getFailure();

        assertThat(failure)
                .isInstanceOf(AiException.class)
                .hasMessageContaining("500");
    }

    @Test
    void chat_modelNotFound_throwsAiException() throws Exception {
        startMockServer((router) -> {
            router.post("/api/chat").handler(ctx -> {
                ctx.response()
                        .setStatusCode(404)
                        .putHeader("Content-Type", "application/json")
                        .end("{\"error\":\"model not found\"}");
            });
        });

        AiRequest request = AiRequest.builder()
                .model("nonexistent-model")
                .addMessage(Message.user("Test"))
                .build();

        Throwable failure = chatModel.chat(request)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitFailure()
                .getFailure();

        assertThat(failure)
                .isInstanceOf(AiException.class)
                .hasMessageContaining("404");
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Streaming Tests (NDJSON Format)
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Test
    void stream_successfulResponse_emitsNDJSONChunks() throws Exception {
        // NDJSON: each line is a complete JSON object
        String mockStreamResponse = """
                {"message":{"content":"Hello"},"done":false}
                {"message":{"content":" world"},"done":false}
                {"message":{"content":"!"},"done":false}
                {"message":{"content":""},"done":true,"prompt_eval_count":10,"eval_count":15}
                """;

        startMockServer((router) -> {
            router.post("/api/chat").handler(ctx -> {
                String body = ctx.body().asString();
                assertThat(body).contains("\"stream\":true");

                ctx.response()
                        .setStatusCode(200)
                        .putHeader("Content-Type", "application/x-ndjson")
                        .end(mockStreamResponse);
            });
        });

        AiRequest request = AiRequest.builder()
                .model("llama3.2")
                .addMessage(Message.user("Say hello"))
                .build();

        List<AiResponse> chunks = chatModel.stream(request)
                .collect().asList()
                .await().atMost(Duration.ofSeconds(5));

        assertThat(chunks).hasSizeGreaterThanOrEqualTo(4);

        // First three chunks have content
        assertThat(chunks.get(0).content()).isEqualTo("Hello");
        assertThat(chunks.get(1).content()).isEqualTo(" world");
        assertThat(chunks.get(2).content()).isEqualTo("!");

        // Last chunk has done=true
        AiResponse lastChunk = chunks.get(3);
        assertThat(lastChunk.content()).isEmpty();
        assertThat(lastChunk.finishReason()).isEqualTo(AiResponse.FinishReason.STOP);
        assertThat(lastChunk.isFinished()).isTrue();
    }

    @Test
    void stream_errorResponse_failsWithException() throws Exception {
        startMockServer((router) -> {
            router.post("/api/chat").handler(ctx -> {
                ctx.response()
                        .setStatusCode(500)
                        .putHeader("Content-Type", "application/json")
                        .end("{\"error\":\"Internal error\"}");
            });
        });

        AiRequest request = AiRequest.builder()
                .model("llama3.2")
                .addMessage(Message.user("Test"))
                .build();

        assertThatThrownBy(() -> {
            chatModel.stream(request)
                    .collect().asList()
                    .await().atMost(Duration.ofSeconds(5));
        }).isInstanceOf(AiException.class)
          .hasMessageContaining("500");
    }

    @Test
    void stream_emptyLines_skippedGracefully() throws Exception {
        String mockStreamResponse = """
                {"message":{"content":"First"},"done":false}

                {"message":{"content":" Second"},"done":false}

                {"message":{"content":""},"done":true}
                """;

        startMockServer((router) -> {
            router.post("/api/chat").handler(ctx -> {
                ctx.response()
                        .setStatusCode(200)
                        .putHeader("Content-Type", "application/x-ndjson")
                        .end(mockStreamResponse);
            });
        });

        AiRequest request = AiRequest.builder()
                .model("llama3.2")
                .addMessage(Message.user("Test"))
                .build();

        List<AiResponse> chunks = chatModel.stream(request)
                .collect().asList()
                .await().atMost(Duration.ofSeconds(5));

        assertThat(chunks).hasSizeGreaterThanOrEqualTo(3);

        List<AiResponse> contentChunks = chunks.stream()
                .filter(chunk -> !chunk.content().isEmpty())
                .toList();

        assertThat(contentChunks.get(0).content()).isEqualTo("First");
        assertThat(contentChunks.get(1).content()).isEqualTo(" Second");
    }

    @Test
    void stream_malformedJSON_skipsUnparseableLines() throws Exception {
        String mockStreamResponse = """
                {"message":{"content":"Valid"},"done":false}
                {malformed json
                {"message":{"content":" Also valid"},"done":false}
                {"message":{"content":""},"done":true}
                """;

        startMockServer((router) -> {
            router.post("/api/chat").handler(ctx -> {
                ctx.response()
                        .setStatusCode(200)
                        .putHeader("Content-Type", "application/x-ndjson")
                        .end(mockStreamResponse);
            });
        });

        AiRequest request = AiRequest.builder()
                .model("llama3.2")
                .addMessage(Message.user("Test"))
                .build();

        List<AiResponse> chunks = chatModel.stream(request)
                .collect().asList()
                .await().atMost(Duration.ofSeconds(5));

        // Should get valid chunks only
        List<AiResponse> contentChunks = chunks.stream()
                .filter(chunk -> !chunk.content().isEmpty())
                .toList();

        assertThat(contentChunks).hasSizeGreaterThanOrEqualTo(2);
        assertThat(contentChunks.get(0).content()).isEqualTo("Valid");
        assertThat(contentChunks.get(1).content()).isEqualTo(" Also valid");
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Request Validation Tests
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Test
    void chat_sendsCorrectModel() throws Exception {
        AtomicInteger callCount = new AtomicInteger(0);

        startMockServer((router) -> {
            router.post("/api/chat").handler(ctx -> {
                callCount.incrementAndGet();
                String body = ctx.body().asString();
                assertThat(body).contains("\"model\":\"llama3.2\"");

                ctx.response()
                        .setStatusCode(200)
                        .putHeader("Content-Type", "application/json")
                        .end("{\"message\":{\"content\":\"OK\"},\"done\":true,\"prompt_eval_count\":5,\"eval_count\":1}");
            });
        });

        AiRequest request = AiRequest.builder()
                .model("llama3.2")
                .addMessage(Message.user("Test"))
                .build();

        chatModel.chat(request)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem(Duration.ofSeconds(5));

        assertThat(callCount.get()).isEqualTo(1);
    }

    @Test
    void chat_sendsStreamFalse() throws Exception {
        AtomicInteger callCount = new AtomicInteger(0);

        startMockServer((router) -> {
            router.post("/api/chat").handler(ctx -> {
                callCount.incrementAndGet();
                String body = ctx.body().asString();
                assertThat(body).contains("\"stream\":false");

                ctx.response()
                        .setStatusCode(200)
                        .putHeader("Content-Type", "application/json")
                        .end("{\"message\":{\"content\":\"OK\"},\"done\":true,\"prompt_eval_count\":5,\"eval_count\":1}");
            });
        });

        AiRequest request = AiRequest.builder()
                .model("llama3.2")
                .addMessage(Message.user("Test"))
                .build();

        chatModel.chat(request)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem(Duration.ofSeconds(5));

        assertThat(callCount.get()).isEqualTo(1);
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Helper Methods
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    private void startMockServer(java.util.function.Consumer<Router> routerConfig) throws Exception {
        Router router = Router.router(vertx);
        router.route().handler(BodyHandler.create());
        routerConfig.accept(router);

        CountDownLatch latch = new CountDownLatch(1);
        mockServer = vertx.createHttpServer()
                .requestHandler(router)
                .listen(MOCK_PORT, ar -> {
                    if (ar.succeeded()) {
                        latch.countDown();
                    }
                });

        if (!latch.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Mock server failed to start");
        }
    }
}
