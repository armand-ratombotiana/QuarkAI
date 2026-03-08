package io.quarkiverse.quarkai.openai;

import io.quarkiverse.quarkai.core.exception.AiAuthException;
import io.quarkiverse.quarkai.core.exception.AiException;
import io.quarkiverse.quarkai.core.exception.AiRateLimitException;
import io.quarkiverse.quarkai.core.model.AiRequest;
import io.quarkiverse.quarkai.core.model.AiResponse;
import io.quarkiverse.quarkai.core.model.Message;
import io.quarkiverse.quarkai.openai.config.OpenAiConfig;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
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
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Integration tests for {@link OpenAiChatModel}.
 *
 * Uses a mock HTTP server (Vert.x) to simulate OpenAI API responses
 * without making real network calls.
 */
@ExtendWith(MockitoExtension.class)
class OpenAiChatModelTest {

    private static final int MOCK_PORT = 18080;
    private static final String MOCK_BASE_URL = "http://localhost:" + MOCK_PORT;

    private Vertx vertx;
    private HttpServer mockServer;
    private OpenAiChatModel chatModel;

    @Mock
    private OpenAiConfig mockConfig;

    @BeforeEach
    void setup() throws Exception {
        vertx = Vertx.vertx();

        // Configure mock config (lenient to avoid strict Mockito checking in streaming tests)
        lenient().when(mockConfig.baseUrl()).thenReturn(MOCK_BASE_URL);
        lenient().when(mockConfig.apiKey()).thenReturn("test-api-key");
        lenient().when(mockConfig.organizationId()).thenReturn(Optional.empty());
        lenient().when(mockConfig.timeoutSeconds()).thenReturn(30L);

        // Create chat model instance
        chatModel = new OpenAiChatModel();
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
                  "id": "chatcmpl-123",
                  "model": "gpt-4o",
                  "choices": [{
                    "message": {"role": "assistant", "content": "Paris is the capital of France."},
                    "finish_reason": "stop"
                  }],
                  "usage": {
                    "prompt_tokens": 20,
                    "completion_tokens": 8,
                    "total_tokens": 28
                  }
                }
                """;

        startMockServer((router) -> {
            router.post("/chat/completions").handler(ctx -> {
                // Verify headers
                assertThat(ctx.request().getHeader("Authorization")).isEqualTo("Bearer test-api-key");
                assertThat(ctx.request().getHeader("Content-Type")).isEqualTo("application/json");

                ctx.response()
                        .setStatusCode(200)
                        .putHeader("Content-Type", "application/json")
                        .end(mockResponse);
            });
        });

        AiRequest request = AiRequest.builder()
                .model("gpt-4o")
                .addMessage(Message.user("What is the capital of France?"))
                .build();

        AiResponse response = chatModel.chat(request)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem(Duration.ofSeconds(5))
                .getItem();

        assertThat(response).isNotNull();
        assertThat(response.content()).isEqualTo("Paris is the capital of France.");
        assertThat(response.finishReason()).isEqualTo(AiResponse.FinishReason.STOP);
        assertThat(response.id()).contains("chatcmpl-123");
        assertThat(response.model()).contains("gpt-4o");
        assertThat(response.usage()).isPresent();
        assertThat(response.usage().get().promptTokens()).isEqualTo(20);
        assertThat(response.usage().get().completionTokens()).isEqualTo(8);
        assertThat(response.usage().get().totalTokens()).isEqualTo(28);
    }

    @Test
    void chat_withSystemMessage_includesSystemInRequest() throws Exception {
        AtomicInteger callCount = new AtomicInteger(0);

        startMockServer((router) -> {
            router.post("/chat/completions").handler(ctx -> {
                callCount.incrementAndGet();
                String body = ctx.body().asString();

                // Verify system message is included
                assertThat(body).contains("\"role\":\"system\"");
                assertThat(body).contains("You are a helpful assistant");

                ctx.response()
                        .setStatusCode(200)
                        .putHeader("Content-Type", "application/json")
                        .end("{\"choices\":[{\"message\":{\"content\":\"OK\"},\"finish_reason\":\"stop\"}]}");
            });
        });

        AiRequest request = AiRequest.builder()
                .model("gpt-4o")
                .addMessage(Message.system("You are a helpful assistant"))
                .addMessage(Message.user("Hello"))
                .build();

        chatModel.chat(request)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem(Duration.ofSeconds(5));

        assertThat(callCount.get()).isEqualTo(1);
    }

    @Test
    void chat_withOrganizationId_includesOrgHeader() throws Exception {
        when(mockConfig.organizationId()).thenReturn(Optional.of("org-abc123"));

        AtomicInteger callCount = new AtomicInteger(0);

        startMockServer((router) -> {
            router.post("/chat/completions").handler(ctx -> {
                callCount.incrementAndGet();
                assertThat(ctx.request().getHeader("OpenAI-Organization")).isEqualTo("org-abc123");

                ctx.response()
                        .setStatusCode(200)
                        .putHeader("Content-Type", "application/json")
                        .end("{\"choices\":[{\"message\":{\"content\":\"OK\"},\"finish_reason\":\"stop\"}]}");
            });
        });

        AiRequest request = AiRequest.builder()
                .model("gpt-4o")
                .addMessage(Message.user("Test"))
                .build();

        chatModel.chat(request)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem(Duration.ofSeconds(5));

        assertThat(callCount.get()).isEqualTo(1);
    }

    @Test
    void chat_lengthFinishReason_mappedCorrectly() throws Exception {
        String mockResponse = """
                {
                  "choices": [{
                    "message": {"role": "assistant", "content": "Truncated..."},
                    "finish_reason": "length"
                  }]
                }
                """;

        startMockServer((router) -> {
            router.post("/chat/completions").handler(ctx -> {
                ctx.response()
                        .setStatusCode(200)
                        .putHeader("Content-Type", "application/json")
                        .end(mockResponse);
            });
        });

        AiRequest request = AiRequest.builder()
                .model("gpt-4o")
                .addMessage(Message.user("Test"))
                .maxTokens(5)
                .build();

        AiResponse response = chatModel.chat(request)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem(Duration.ofSeconds(5))
                .getItem();

        assertThat(response.finishReason()).isEqualTo(AiResponse.FinishReason.LENGTH);
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Error Handling Tests (Non-Streaming)
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Test
    void chat_authError_throwsAiAuthException() throws Exception {
        startMockServer((router) -> {
            router.post("/chat/completions").handler(ctx -> {
                ctx.response()
                        .setStatusCode(401)
                        .putHeader("Content-Type", "application/json")
                        .end("{\"error\":{\"message\":\"Invalid API key\"}}");
            });
        });

        AiRequest request = AiRequest.builder()
                .model("gpt-4o")
                .addMessage(Message.user("Test"))
                .build();

        Throwable failure = chatModel.chat(request)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitFailure()
                .getFailure();

        assertThat(failure)
                .isInstanceOf(AiAuthException.class)
                .hasMessageContaining("authentication failed");
    }

    @Test
    void chat_rateLimitError_throwsAiRateLimitException() throws Exception {
        startMockServer((router) -> {
            router.post("/chat/completions").handler(ctx -> {
                ctx.response()
                        .setStatusCode(429)
                        .putHeader("Content-Type", "application/json")
                        .end("{\"error\":{\"message\":\"Rate limit exceeded\"}}");
            });
        });

        AiRequest request = AiRequest.builder()
                .model("gpt-4o")
                .addMessage(Message.user("Test"))
                .build();

        Throwable failure = chatModel.chat(request)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitFailure()
                .getFailure();

        assertThat(failure)
                .isInstanceOf(AiRateLimitException.class)
                .hasMessageContaining("rate limit");
    }

    @Test
    void chat_serverError_throwsAiException() throws Exception {
        startMockServer((router) -> {
            router.post("/chat/completions").handler(ctx -> {
                ctx.response()
                        .setStatusCode(500)
                        .putHeader("Content-Type", "application/json")
                        .end("{\"error\":{\"message\":\"Internal server error\"}}");
            });
        });

        AiRequest request = AiRequest.builder()
                .model("gpt-4o")
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

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Streaming Tests
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Test
    void stream_successfulResponse_emitsMultipleChunks() throws Exception {
        String mockStreamResponse = """
                data: {"id":"1","model":"gpt-4o","choices":[{"delta":{"content":"Hello"},"finish_reason":null}]}

                data: {"id":"1","model":"gpt-4o","choices":[{"delta":{"content":" world"},"finish_reason":null}]}

                data: {"id":"1","model":"gpt-4o","choices":[{"delta":{"content":"!"},"finish_reason":"stop"}]}

                data: [DONE]
                """;

        startMockServer((router) -> {
            router.post("/chat/completions").handler(ctx -> {
                String body = ctx.body().asString();
                assertThat(body).contains("\"stream\":true");

                ctx.response()
                        .setStatusCode(200)
                        .putHeader("Content-Type", "text/event-stream")
                        .end(mockStreamResponse);
            });
        });

        AiRequest request = AiRequest.builder()
                .model("gpt-4o")
                .addMessage(Message.user("Say hello"))
                .build();

        List<AiResponse> chunks = chatModel.stream(request)
                .collect().asList()
                .await().atMost(Duration.ofSeconds(5));

        assertThat(chunks).hasSize(3);
        assertThat(chunks.get(0).content()).isEqualTo("Hello");
        assertThat(chunks.get(1).content()).isEqualTo(" world");
        assertThat(chunks.get(2).content()).isEqualTo("!");
        assertThat(chunks.get(2).finishReason()).isEqualTo(AiResponse.FinishReason.STOP);
        assertThat(chunks.get(2).isFinished()).isTrue();
    }

    @Test
    void stream_errorResponse_failsWithException() throws Exception {
        startMockServer((router) -> {
            router.post("/chat/completions").handler(ctx -> {
                ctx.response()
                        .setStatusCode(401)
                        .putHeader("Content-Type", "application/json")
                        .end("{\"error\":{\"message\":\"Invalid API key\"}}");
            });
        });

        AiRequest request = AiRequest.builder()
                .model("gpt-4o")
                .addMessage(Message.user("Test"))
                .build();

        assertThatThrownBy(() -> {
            chatModel.stream(request)
                    .collect().asList()
                    .await().atMost(Duration.ofSeconds(5));
        }).isInstanceOf(AiAuthException.class);
    }

    @Test
    void stream_emptyResponse_completesWithoutChunks() throws Exception {
        String mockStreamResponse = "data: [DONE]\n";

        startMockServer((router) -> {
            router.post("/chat/completions").handler(ctx -> {
                ctx.response()
                        .setStatusCode(200)
                        .putHeader("Content-Type", "text/event-stream")
                        .end(mockStreamResponse);
            });
        });

        AiRequest request = AiRequest.builder()
                .model("gpt-4o")
                .addMessage(Message.user("Test"))
                .build();

        List<AiResponse> chunks = chatModel.stream(request)
                .collect().asList()
                .await().atMost(Duration.ofSeconds(5));

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).isFinished()).isTrue();
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Request Validation Tests
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Test
    void chat_sendsCorrectTemperature() throws Exception {
        AtomicInteger callCount = new AtomicInteger(0);

        startMockServer((router) -> {
            router.post("/chat/completions").handler(ctx -> {
                callCount.incrementAndGet();
                String body = ctx.body().asString();
                assertThat(body).contains("\"temperature\":0.7");

                ctx.response()
                        .setStatusCode(200)
                        .putHeader("Content-Type", "application/json")
                        .end("{\"choices\":[{\"message\":{\"content\":\"OK\"},\"finish_reason\":\"stop\"}]}");
            });
        });

        AiRequest request = AiRequest.builder()
                .model("gpt-4o")
                .addMessage(Message.user("Test"))
                .temperature(0.7)
                .build();

        chatModel.chat(request)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem(Duration.ofSeconds(5));

        assertThat(callCount.get()).isEqualTo(1);
    }

    @Test
    void chat_sendsCorrectMaxTokens() throws Exception {
        AtomicInteger callCount = new AtomicInteger(0);

        startMockServer((router) -> {
            router.post("/chat/completions").handler(ctx -> {
                callCount.incrementAndGet();
                String body = ctx.body().asString();
                assertThat(body).contains("\"max_tokens\":500");

                ctx.response()
                        .setStatusCode(200)
                        .putHeader("Content-Type", "application/json")
                        .end("{\"choices\":[{\"message\":{\"content\":\"OK\"},\"finish_reason\":\"stop\"}]}");
            });
        });

        AiRequest request = AiRequest.builder()
                .model("gpt-4o")
                .addMessage(Message.user("Test"))
                .maxTokens(500)
                .build();

        chatModel.chat(request)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem(Duration.ofSeconds(5));

        assertThat(callCount.get()).isEqualTo(1);
    }

    @Test
    void chat_sendsCorrectUserId() throws Exception {
        AtomicInteger callCount = new AtomicInteger(0);

        startMockServer((router) -> {
            router.post("/chat/completions").handler(ctx -> {
                callCount.incrementAndGet();
                String body = ctx.body().asString();
                assertThat(body).contains("\"user\":\"user-123\"");

                ctx.response()
                        .setStatusCode(200)
                        .putHeader("Content-Type", "application/json")
                        .end("{\"choices\":[{\"message\":{\"content\":\"OK\"},\"finish_reason\":\"stop\"}]}");
            });
        });

        AiRequest request = AiRequest.builder()
                .model("gpt-4o")
                .addMessage(Message.user("Test"))
                .userId("user-123")
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
