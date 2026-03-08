package io.quarkiverse.quarkai.vertex;

import io.quarkiverse.quarkai.core.exception.AiAuthException;
import io.quarkiverse.quarkai.core.exception.AiException;
import io.quarkiverse.quarkai.core.model.AiRequest;
import io.quarkiverse.quarkai.core.model.AiResponse;
import io.quarkiverse.quarkai.core.model.Message;
import io.quarkiverse.quarkai.vertex.config.VertexAiConfig;
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
 * Integration tests for {@link VertexAiChatModel}.
 *
 * Tests Vertex AI (Gemini)-specific protocol details:
 * - Dynamic URL construction with location and projectId
 * - OAuth2 Bearer token authentication
 * - systemInstruction field for system messages
 * - JSON array streaming format
 */
@ExtendWith(MockitoExtension.class)
class VertexAiChatModelTest {

    private static final int MOCK_PORT = 18082;
    private static final String MOCK_LOCATION = "us-central1";
    private static final String MOCK_PROJECT_ID = "test-project";
    private static final String MOCK_MODEL = "gemini-1.5-flash";

    private Vertx vertx;
    private HttpServer mockServer;
    private VertexAiChatModel chatModel;

    @Mock
    private VertexAiConfig mockConfig;

    @BeforeEach
    void setup() {
        vertx = Vertx.vertx();

        when(mockConfig.location()).thenReturn(MOCK_LOCATION);
        when(mockConfig.projectId()).thenReturn(MOCK_PROJECT_ID);
        when(mockConfig.model()).thenReturn(MOCK_MODEL);
        when(mockConfig.accessToken()).thenReturn("test-oauth-token");
        when(mockConfig.timeoutSeconds()).thenReturn(30L);

        chatModel = new VertexAiChatModel();
        chatModel.config = mockConfig;
        chatModel.vertx = vertx;

        // Override baseUrl for testing to use mock server
        chatModel.init();
        // Manually set baseUrl to point to mock server
        chatModel.baseUrl = "http://localhost:" + MOCK_PORT + "/v1/projects/" + MOCK_PROJECT_ID
                + "/locations/" + MOCK_LOCATION + "/publishers/google/models/" + MOCK_MODEL;
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
                  "candidates": [{
                    "content": {
                      "parts": [{"text": "Paris is the capital of France."}],
                      "role": "model"
                    },
                    "finishReason": "STOP"
                  }],
                  "usageMetadata": {
                    "promptTokenCount": 12,
                    "candidatesTokenCount": 7,
                    "totalTokenCount": 19
                  }
                }
                """;

        startMockServer((router) -> {
            router.postWithRegex("/v1/projects/[^/]+/locations/[^/]+/publishers/google/models/.+\\:(generateContent|streamGenerateContent)")
                    .handler(ctx -> {
                        // Verify OAuth2 Bearer token
                        assertThat(ctx.request().getHeader("Authorization"))
                                .isEqualTo("Bearer test-oauth-token");
                        assertThat(ctx.request().getHeader("Content-Type"))
                                .isEqualTo("application/json");

                        ctx.response()
                                .setStatusCode(200)
                                .putHeader("Content-Type", "application/json")
                                .end(mockResponse);
                    });
        });

        AiRequest request = AiRequest.builder()
                .model("gemini-1.5-flash")
                .addMessage(Message.user("What is the capital of France?"))
                .build();

        AiResponse response = chatModel.chat(request)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem(Duration.ofSeconds(5))
                .getItem();

        assertThat(response).isNotNull();
        assertThat(response.content()).isEqualTo("Paris is the capital of France.");
        assertThat(response.finishReason()).isEqualTo(AiResponse.FinishReason.STOP);
        assertThat(response.usage()).isPresent();
        assertThat(response.usage().get().promptTokens()).isEqualTo(12);
        assertThat(response.usage().get().completionTokens()).isEqualTo(7);
    }

    @Test
    void chat_withSystemMessage_usesSystemInstruction() throws Exception {
        AtomicInteger callCount = new AtomicInteger(0);

        startMockServer((router) -> {
            router.postWithRegex("/v1/projects/[^/]+/locations/[^/]+/publishers/google/models/.+\\:(generateContent|streamGenerateContent)")
                    .handler(ctx -> {
                        callCount.incrementAndGet();
                        String body = ctx.body().asString();

                        // Verify systemInstruction field exists
                        assertThat(body).contains("\"systemInstruction\"");
                        assertThat(body).contains("You are a helpful assistant");
                        // Verify system message is NOT in contents array
                        assertThat(body).doesNotContain("\"role\":\"system\"");

                        ctx.response()
                                .setStatusCode(200)
                                .putHeader("Content-Type", "application/json")
                                .end("{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"OK\"}]},\"finishReason\":\"STOP\"}],\"usageMetadata\":{\"promptTokenCount\":5,\"candidatesTokenCount\":1}}");
                    });
        });

        AiRequest request = AiRequest.builder()
                .model("gemini-1.5-flash")
                .addMessage(Message.system("You are a helpful assistant"))
                .addMessage(Message.user("Hello"))
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
                  "candidates": [{
                    "content": {
                      "parts": [{"text": "Truncated..."}]
                    },
                    "finishReason": "MAX_TOKENS"
                  }],
                  "usageMetadata": {
                    "promptTokenCount": 10,
                    "candidatesTokenCount": 5
                  }
                }
                """;

        startMockServer((router) -> {
            router.postWithRegex("/v1/projects/[^/]+/locations/[^/]+/publishers/google/models/.+\\:(generateContent|streamGenerateContent)")
                    .handler(ctx -> {
                        ctx.response()
                                .setStatusCode(200)
                                .putHeader("Content-Type", "application/json")
                                .end(mockResponse);
                    });
        });

        AiRequest request = AiRequest.builder()
                .model("gemini-1.5-flash")
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
    void chat_safetyFinishReason_mappedToContentFilter() throws Exception {
        String mockResponse = """
                {
                  "candidates": [{
                    "content": {
                      "parts": [{"text": ""}]
                    },
                    "finishReason": "SAFETY"
                  }],
                  "usageMetadata": {
                    "promptTokenCount": 10,
                    "candidatesTokenCount": 0
                  }
                }
                """;

        startMockServer((router) -> {
            router.postWithRegex("/v1/projects/[^/]+/locations/[^/]+/publishers/google/models/.+\\:(generateContent|streamGenerateContent)")
                    .handler(ctx -> {
                        ctx.response()
                                .setStatusCode(200)
                                .putHeader("Content-Type", "application/json")
                                .end(mockResponse);
                    });
        });

        AiRequest request = AiRequest.builder()
                .model("gemini-1.5-flash")
                .addMessage(Message.user("Test"))
                .build();

        AiResponse response = chatModel.chat(request)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem(Duration.ofSeconds(5))
                .getItem();

        assertThat(response.finishReason()).isEqualTo(AiResponse.FinishReason.CONTENT_FILTER);
    }

    @Test
    void chat_userAndModelRoles_mappedCorrectly() throws Exception {
        AtomicInteger callCount = new AtomicInteger(0);

        startMockServer((router) -> {
            router.postWithRegex("/v1/projects/[^/]+/locations/[^/]+/publishers/google/models/.+\\:(generateContent|streamGenerateContent)")
                    .handler(ctx -> {
                        callCount.incrementAndGet();
                        String body = ctx.body().asString();

                        // Verify role mapping: USER -> user, ASSISTANT -> model
                        assertThat(body).contains("\"role\":\"user\"");
                        assertThat(body).contains("\"role\":\"model\"");

                        ctx.response()
                                .setStatusCode(200)
                                .putHeader("Content-Type", "application/json")
                                .end("{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"OK\"}]},\"finishReason\":\"STOP\"}],\"usageMetadata\":{\"promptTokenCount\":5,\"candidatesTokenCount\":1}}");
                    });
        });

        AiRequest request = AiRequest.builder()
                .model("gemini-1.5-flash")
                .addMessage(Message.user("First message"))
                .addMessage(Message.assistant("Assistant response"))
                .addMessage(Message.user("Follow up"))
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
    void chat_authError401_throwsAiAuthException() throws Exception {
        startMockServer((router) -> {
            router.postWithRegex("/v1/projects/[^/]+/locations/[^/]+/publishers/google/models/.+\\:(generateContent|streamGenerateContent)")
                    .handler(ctx -> {
                        ctx.response()
                                .setStatusCode(401)
                                .putHeader("Content-Type", "application/json")
                                .end("{\"error\":{\"message\":\"Invalid credentials\"}}");
                    });
        });

        AiRequest request = AiRequest.builder()
                .model("gemini-1.5-flash")
                .addMessage(Message.user("Test"))
                .build();

        Throwable failure = chatModel.chat(request)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitFailure()
                .getFailure();

        assertThat(failure)
                .isInstanceOf(AiAuthException.class)
                .hasMessageContaining("Vertex AI auth failed");
    }

    @Test
    void chat_authError403_throwsAiAuthException() throws Exception {
        startMockServer((router) -> {
            router.postWithRegex("/v1/projects/[^/]+/locations/[^/]+/publishers/google/models/.+\\:(generateContent|streamGenerateContent)")
                    .handler(ctx -> {
                        ctx.response()
                                .setStatusCode(403)
                                .putHeader("Content-Type", "application/json")
                                .end("{\"error\":{\"message\":\"Permission denied\"}}");
                    });
        });

        AiRequest request = AiRequest.builder()
                .model("gemini-1.5-flash")
                .addMessage(Message.user("Test"))
                .build();

        Throwable failure = chatModel.chat(request)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitFailure()
                .getFailure();

        assertThat(failure)
                .isInstanceOf(AiAuthException.class)
                .hasMessageContaining("Vertex AI auth failed");
    }

    @Test
    void chat_serverError_throwsAiException() throws Exception {
        startMockServer((router) -> {
            router.postWithRegex("/v1/projects/[^/]+/locations/[^/]+/publishers/google/models/.+\\:(generateContent|streamGenerateContent)")
                    .handler(ctx -> {
                        ctx.response()
                                .setStatusCode(500)
                                .putHeader("Content-Type", "application/json")
                                .end("{\"error\":{\"message\":\"Internal error\"}}");
                    });
        });

        AiRequest request = AiRequest.builder()
                .model("gemini-1.5-flash")
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
        // Vertex streams a JSON array of responses
        String mockStreamResponse = """
                [
                  {"candidates":[{"content":{"parts":[{"text":"Hello"}]},"finishReason":""}]},
                  {"candidates":[{"content":{"parts":[{"text":" world"}]},"finishReason":""}]},
                  {"candidates":[{"content":{"parts":[{"text":"!"}]},"finishReason":"STOP"}]}
                ]
                """;

        startMockServer((router) -> {
            router.postWithRegex("/v1/projects/[^/]+/locations/[^/]+/publishers/google/models/.+\\:(generateContent|streamGenerateContent)")
                    .handler(ctx -> {
                        ctx.response()
                                .setStatusCode(200)
                                .putHeader("Content-Type", "application/json")
                                .end(mockStreamResponse);
                    });
        });

        AiRequest request = AiRequest.builder()
                .model("gemini-1.5-flash")
                .addMessage(Message.user("Say hello"))
                .build();

        List<AiResponse> chunks = chatModel.stream(request)
                .collect().asList()
                .await().atMost(Duration.ofSeconds(5));

        assertThat(chunks).hasSizeGreaterThanOrEqualTo(3);

        // Filter content chunks
        List<AiResponse> contentChunks = chunks.stream()
                .filter(chunk -> !chunk.content().isEmpty())
                .toList();

        assertThat(contentChunks.get(0).content()).isEqualTo("Hello");
        assertThat(contentChunks.get(1).content()).isEqualTo(" world");
        assertThat(contentChunks.get(2).content()).isEqualTo("!");

        // Last chunk should be stop marker
        AiResponse lastChunk = chunks.get(chunks.size() - 1);
        assertThat(lastChunk.finishReason()).isEqualTo(AiResponse.FinishReason.STOP);
    }

    @Test
    void stream_errorResponse_failsWithException() throws Exception {
        startMockServer((router) -> {
            router.postWithRegex("/v1/projects/[^/]+/locations/[^/]+/publishers/google/models/.+\\:(generateContent|streamGenerateContent)")
                    .handler(ctx -> {
                        ctx.response()
                                .setStatusCode(401)
                                .putHeader("Content-Type", "application/json")
                                .end("{\"error\":{\"message\":\"Unauthorized\"}}");
                    });
        });

        AiRequest request = AiRequest.builder()
                .model("gemini-1.5-flash")
                .addMessage(Message.user("Test"))
                .build();

        assertThatThrownBy(() -> {
            chatModel.stream(request)
                    .collect().asList()
                    .await().atMost(Duration.ofSeconds(5));
        }).isInstanceOf(AiAuthException.class);
    }

    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
    // Request Configuration Tests
    // ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

    @Test
    void chat_sendsCorrectGenerationConfig() throws Exception {
        AtomicInteger callCount = new AtomicInteger(0);

        startMockServer((router) -> {
            router.postWithRegex("/v1/projects/[^/]+/locations/[^/]+/publishers/google/models/.+\\:(generateContent|streamGenerateContent)")
                    .handler(ctx -> {
                        callCount.incrementAndGet();
                        String body = ctx.body().asString();

                        assertThat(body).contains("\"generationConfig\"");
                        assertThat(body).contains("\"temperature\":0.7");
                        assertThat(body).contains("\"maxOutputTokens\":1024");

                        ctx.response()
                                .setStatusCode(200)
                                .putHeader("Content-Type", "application/json")
                                .end("{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"OK\"}]},\"finishReason\":\"STOP\"}],\"usageMetadata\":{\"promptTokenCount\":5,\"candidatesTokenCount\":1}}");
                    });
        });

        AiRequest request = AiRequest.builder()
                .model("gemini-1.5-flash")
                .addMessage(Message.user("Test"))
                .temperature(0.7)
                .maxTokens(1024)
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
