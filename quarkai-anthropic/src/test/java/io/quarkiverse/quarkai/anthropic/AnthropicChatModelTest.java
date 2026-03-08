package io.quarkiverse.quarkai.anthropic;

import io.quarkiverse.quarkai.anthropic.config.AnthropicConfig;
import io.quarkiverse.quarkai.core.exception.AiAuthException;
import io.quarkiverse.quarkai.core.exception.AiException;
import io.quarkiverse.quarkai.core.exception.AiRateLimitException;
import io.quarkiverse.quarkai.core.model.AiRequest;
import io.quarkiverse.quarkai.core.model.AiResponse;
import io.quarkiverse.quarkai.core.model.Message;
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
 * Integration tests for {@link AnthropicChatModel}.
 *
 * Tests Anthropic-specific protocol details:
 * - System message hoisting to top-level "system" field
 * - x-api-key header authentication
 * - anthropic-version header
 * - Content block delta streaming events
 */
@ExtendWith(MockitoExtension.class)
class AnthropicChatModelTest {

    private static final int MOCK_PORT = 18081;
    private static final String MOCK_BASE_URL = "http://localhost:" + MOCK_PORT;

    private Vertx vertx;
    private HttpServer mockServer;
    private AnthropicChatModel chatModel;

    @Mock
    private AnthropicConfig mockConfig;

    @BeforeEach
    void setup() {
        vertx = Vertx.vertx();

        when(mockConfig.baseUrl()).thenReturn(MOCK_BASE_URL);
        when(mockConfig.apiKey()).thenReturn("test-anthropic-key");
        when(mockConfig.apiVersion()).thenReturn("2023-06-01");
        when(mockConfig.timeoutSeconds()).thenReturn(30L);

        chatModel = new AnthropicChatModel();
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
                  "id": "msg_abc123",
                  "model": "claude-sonnet-4-5-20250929",
                  "content": [{
                    "type": "text",
                    "text": "Paris is the capital of France."
                  }],
                  "stop_reason": "end_turn",
                  "usage": {
                    "input_tokens": 15,
                    "output_tokens": 8
                  }
                }
                """;

        startMockServer((router) -> {
            router.post("/messages").handler(ctx -> {
                // Verify Anthropic-specific headers
                assertThat(ctx.request().getHeader("x-api-key")).isEqualTo("test-anthropic-key");
                assertThat(ctx.request().getHeader("anthropic-version")).isEqualTo("2023-06-01");
                assertThat(ctx.request().getHeader("Content-Type")).isEqualTo("application/json");

                ctx.response()
                        .setStatusCode(200)
                        .putHeader("Content-Type", "application/json")
                        .end(mockResponse);
            });
        });

        AiRequest request = AiRequest.builder()
                .model("claude-sonnet-4-5-20250929")
                .addMessage(Message.user("What is the capital of France?"))
                .build();

        AiResponse response = chatModel.chat(request)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem(Duration.ofSeconds(5))
                .getItem();

        assertThat(response).isNotNull();
        assertThat(response.content()).isEqualTo("Paris is the capital of France.");
        assertThat(response.finishReason()).isEqualTo(AiResponse.FinishReason.STOP);
        assertThat(response.id()).contains("msg_abc123");
        assertThat(response.model()).contains("claude-sonnet-4-5-20250929");
        assertThat(response.usage()).isPresent();
        assertThat(response.usage().get().promptTokens()).isEqualTo(15);
        assertThat(response.usage().get().completionTokens()).isEqualTo(8);
    }

    @Test
    void chat_withSystemMessage_hoistsToSystemField() throws Exception {
        AtomicInteger callCount = new AtomicInteger(0);

        startMockServer((router) -> {
            router.post("/messages").handler(ctx -> {
                callCount.incrementAndGet();
                String body = ctx.body().asString();

                // Verify system message is hoisted to top-level "system" field
                assertThat(body).contains("\"system\":\"You are a helpful coding assistant\"");
                // Verify system message is NOT in messages array
                assertThat(body).doesNotContain("\"role\":\"system\"");
                // Verify user message is in messages array
                assertThat(body).contains("\"role\":\"user\"");

                ctx.response()
                        .setStatusCode(200)
                        .putHeader("Content-Type", "application/json")
                        .end("{\"content\":[{\"text\":\"OK\"}],\"stop_reason\":\"end_turn\",\"usage\":{\"input_tokens\":10,\"output_tokens\":2}}");
            });
        });

        AiRequest request = AiRequest.builder()
                .model("claude-sonnet-4-5-20250929")
                .addMessage(Message.system("You are a helpful coding assistant"))
                .addMessage(Message.user("Hello"))
                .build();

        chatModel.chat(request)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem(Duration.ofSeconds(5));

        assertThat(callCount.get()).isEqualTo(1);
    }

    @Test
    void chat_multipleSystemMessages_combinesWithNewline() throws Exception {
        AtomicInteger callCount = new AtomicInteger(0);

        startMockServer((router) -> {
            router.post("/messages").handler(ctx -> {
                callCount.incrementAndGet();
                String body = ctx.body().asString();

                // Verify multiple system messages are combined with newline
                assertThat(body).contains("\"system\":\"System instruction 1\\nSystem instruction 2\"");

                ctx.response()
                        .setStatusCode(200)
                        .putHeader("Content-Type", "application/json")
                        .end("{\"content\":[{\"text\":\"OK\"}],\"stop_reason\":\"end_turn\",\"usage\":{\"input_tokens\":10,\"output_tokens\":2}}");
            });
        });

        AiRequest request = AiRequest.builder()
                .model("claude-sonnet-4-5-20250929")
                .addMessage(Message.system("System instruction 1"))
                .addMessage(Message.system("System instruction 2"))
                .addMessage(Message.user("Test"))
                .build();

        chatModel.chat(request)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem(Duration.ofSeconds(5));

        assertThat(callCount.get()).isEqualTo(1);
    }

    @Test
    void chat_maxTokensFinishReason_mappedToLength() throws Exception {
        String mockResponse = """
                {
                  "content": [{
                    "text": "Truncated response..."
                  }],
                  "stop_reason": "max_tokens",
                  "usage": {
                    "input_tokens": 10,
                    "output_tokens": 5
                  }
                }
                """;

        startMockServer((router) -> {
            router.post("/messages").handler(ctx -> {
                ctx.response()
                        .setStatusCode(200)
                        .putHeader("Content-Type", "application/json")
                        .end(mockResponse);
            });
        });

        AiRequest request = AiRequest.builder()
                .model("claude-sonnet-4-5-20250929")
                .addMessage(Message.user("Test"))
                .maxTokens(5)
                .build();

        AiResponse response = chatModel.chat(request)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem(Duration.ofSeconds(5))
                .getItem();

        assertThat(response.finishReason()).isEqualTo(AiResponse.FinishReason.LENGTH);
    }

    @Test
    void chat_assistantMessage_includesInMessages() throws Exception {
        AtomicInteger callCount = new AtomicInteger(0);

        startMockServer((router) -> {
            router.post("/messages").handler(ctx -> {
                callCount.incrementAndGet();
                String body = ctx.body().asString();

                // Verify assistant message is in messages array with correct role
                assertThat(body).contains("\"role\":\"assistant\"");
                assertThat(body).contains("Previous assistant response");

                ctx.response()
                        .setStatusCode(200)
                        .putHeader("Content-Type", "application/json")
                        .end("{\"content\":[{\"text\":\"OK\"}],\"stop_reason\":\"end_turn\",\"usage\":{\"input_tokens\":10,\"output_tokens\":2}}");
            });
        });

        AiRequest request = AiRequest.builder()
                .model("claude-sonnet-4-5-20250929")
                .addMessage(Message.user("First question"))
                .addMessage(Message.assistant("Previous assistant response"))
                .addMessage(Message.user("Follow up question"))
                .build();

        chatModel.chat(request)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem(Duration.ofSeconds(5));

        assertThat(callCount.get()).isEqualTo(1);
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Error Handling Tests (Non-Streaming)
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Test
    void chat_authError_throwsAiAuthException() throws Exception {
        startMockServer((router) -> {
            router.post("/messages").handler(ctx -> {
                ctx.response()
                        .setStatusCode(401)
                        .putHeader("Content-Type", "application/json")
                        .end("{\"error\":{\"message\":\"Invalid API key\"}}");
            });
        });

        AiRequest request = AiRequest.builder()
                .model("claude-sonnet-4-5-20250929")
                .addMessage(Message.user("Test"))
                .build();

        Throwable failure = chatModel.chat(request)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitFailure()
                .getFailure();

        assertThat(failure)
                .isInstanceOf(AiAuthException.class)
                .hasMessageContaining("Anthropic auth failed");
    }

    @Test
    void chat_rateLimitError_throwsAiRateLimitException() throws Exception {
        startMockServer((router) -> {
            router.post("/messages").handler(ctx -> {
                ctx.response()
                        .setStatusCode(429)
                        .putHeader("Content-Type", "application/json")
                        .end("{\"error\":{\"message\":\"Rate limit exceeded\"}}");
            });
        });

        AiRequest request = AiRequest.builder()
                .model("claude-sonnet-4-5-20250929")
                .addMessage(Message.user("Test"))
                .build();

        Throwable failure = chatModel.chat(request)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitFailure()
                .getFailure();

        assertThat(failure)
                .isInstanceOf(AiRateLimitException.class)
                .hasMessageContaining("Anthropic rate limit");
    }

    @Test
    void chat_serverError_throwsAiException() throws Exception {
        startMockServer((router) -> {
            router.post("/messages").handler(ctx -> {
                ctx.response()
                        .setStatusCode(500)
                        .putHeader("Content-Type", "application/json")
                        .end("{\"error\":{\"message\":\"Internal server error\"}}");
            });
        });

        AiRequest request = AiRequest.builder()
                .model("claude-sonnet-4-5-20250929")
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
    void stream_successfulResponse_emitsContentBlockDeltas() throws Exception {
        String mockStreamResponse = """
                event: message_start
                data: {"type":"message_start","message":{"id":"msg_1","model":"claude-sonnet-4-5-20250929"}}

                event: content_block_delta
                data: {"type":"content_block_delta","delta":{"type":"text_delta","text":"Hello"}}

                event: content_block_delta
                data: {"type":"content_block_delta","delta":{"type":"text_delta","text":" world"}}

                event: content_block_delta
                data: {"type":"content_block_delta","delta":{"type":"text_delta","text":"!"}}

                event: message_stop
                data: {"type":"message_stop"}
                """;

        startMockServer((router) -> {
            router.post("/messages").handler(ctx -> {
                String body = ctx.body().asString();
                assertThat(body).contains("\"stream\":true");

                ctx.response()
                        .setStatusCode(200)
                        .putHeader("Content-Type", "text/event-stream")
                        .end(mockStreamResponse);
            });
        });

        AiRequest request = AiRequest.builder()
                .model("claude-sonnet-4-5-20250929")
                .addMessage(Message.user("Say hello"))
                .build();

        List<AiResponse> chunks = chatModel.stream(request)
                .collect().asList()
                .await().atMost(Duration.ofSeconds(5));

        assertThat(chunks).hasSizeGreaterThanOrEqualTo(3);

        // Filter to content chunks
        List<AiResponse> contentChunks = chunks.stream()
                .filter(chunk -> !chunk.content().isEmpty())
                .toList();

        assertThat(contentChunks.get(0).content()).isEqualTo("Hello");
        assertThat(contentChunks.get(1).content()).isEqualTo(" world");
        assertThat(contentChunks.get(2).content()).isEqualTo("!");

        // Verify last chunk is finish marker
        AiResponse lastChunk = chunks.get(chunks.size() - 1);
        assertThat(lastChunk.content()).isEmpty();
        assertThat(lastChunk.finishReason()).isEqualTo(AiResponse.FinishReason.STOP);
        assertThat(lastChunk.isFinished()).isTrue();
    }

    @Test
    void stream_errorResponse_failsWithException() throws Exception {
        startMockServer((router) -> {
            router.post("/messages").handler(ctx -> {
                ctx.response()
                        .setStatusCode(401)
                        .putHeader("Content-Type", "application/json")
                        .end("{\"error\":{\"message\":\"Invalid API key\"}}");
            });
        });

        AiRequest request = AiRequest.builder()
                .model("claude-sonnet-4-5-20250929")
                .addMessage(Message.user("Test"))
                .build();

        assertThatThrownBy(() -> {
            chatModel.stream(request)
                    .collect().asList()
                    .await().atMost(Duration.ofSeconds(5));
        }).isInstanceOf(AiAuthException.class);
    }

    @Test
    void stream_emptyResponse_completesWithStopMarker() throws Exception {
        String mockStreamResponse = """
                event: message_stop
                data: {"type":"message_stop"}
                """;

        startMockServer((router) -> {
            router.post("/messages").handler(ctx -> {
                ctx.response()
                        .setStatusCode(200)
                        .putHeader("Content-Type", "text/event-stream")
                        .end(mockStreamResponse);
            });
        });

        AiRequest request = AiRequest.builder()
                .model("claude-sonnet-4-5-20250929")
                .addMessage(Message.user("Test"))
                .build();

        List<AiResponse> chunks = chatModel.stream(request)
                .collect().asList()
                .await().atMost(Duration.ofSeconds(5));

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).isFinished()).isTrue();
    }

    @Test
    void stream_malformedChunks_skipsUnparseableLines() throws Exception {
        String mockStreamResponse = """
                event: content_block_delta
                data: {"type":"content_block_delta","delta":{"text":"Valid chunk"}}

                event: invalid
                data: {malformed json

                event: content_block_delta
                data: {"type":"content_block_delta","delta":{"text":" Another valid"}}

                event: message_stop
                data: {"type":"message_stop"}
                """;

        startMockServer((router) -> {
            router.post("/messages").handler(ctx -> {
                ctx.response()
                        .setStatusCode(200)
                        .putHeader("Content-Type", "text/event-stream")
                        .end(mockStreamResponse);
            });
        });

        AiRequest request = AiRequest.builder()
                .model("claude-sonnet-4-5-20250929")
                .addMessage(Message.user("Test"))
                .build();

        List<AiResponse> chunks = chatModel.stream(request)
                .collect().asList()
                .await().atMost(Duration.ofSeconds(5));

        // Should only get valid chunks + stop marker
        List<AiResponse> contentChunks = chunks.stream()
                .filter(chunk -> !chunk.content().isEmpty())
                .toList();

        assertThat(contentChunks).hasSizeGreaterThanOrEqualTo(2);
        assertThat(contentChunks.get(0).content()).isEqualTo("Valid chunk");
        assertThat(contentChunks.get(1).content()).isEqualTo(" Another valid");
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Request Validation Tests
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Test
    void chat_sendsCorrectModel() throws Exception {
        AtomicInteger callCount = new AtomicInteger(0);

        startMockServer((router) -> {
            router.post("/messages").handler(ctx -> {
                callCount.incrementAndGet();
                String body = ctx.body().asString();
                assertThat(body).contains("\"model\":\"claude-sonnet-4-5-20250929\"");

                ctx.response()
                        .setStatusCode(200)
                        .putHeader("Content-Type", "application/json")
                        .end("{\"content\":[{\"text\":\"OK\"}],\"stop_reason\":\"end_turn\",\"usage\":{\"input_tokens\":10,\"output_tokens\":2}}");
            });
        });

        AiRequest request = AiRequest.builder()
                .model("claude-sonnet-4-5-20250929")
                .addMessage(Message.user("Test"))
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
            router.post("/messages").handler(ctx -> {
                callCount.incrementAndGet();
                String body = ctx.body().asString();
                assertThat(body).contains("\"max_tokens\":4096");

                ctx.response()
                        .setStatusCode(200)
                        .putHeader("Content-Type", "application/json")
                        .end("{\"content\":[{\"text\":\"OK\"}],\"stop_reason\":\"end_turn\",\"usage\":{\"input_tokens\":10,\"output_tokens\":2}}");
            });
        });

        AiRequest request = AiRequest.builder()
                .model("claude-sonnet-4-5-20250929")
                .addMessage(Message.user("Test"))
                .maxTokens(4096)
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
