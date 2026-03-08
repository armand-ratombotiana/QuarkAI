package io.quarkiverse.quarkai.openai;

import io.quarkiverse.quarkai.core.exception.AiException;
import io.quarkiverse.quarkai.core.model.AiRequest;
import io.quarkiverse.quarkai.core.model.AiResponse;
import io.quarkiverse.quarkai.core.spi.ChatModel;
import io.quarkiverse.quarkai.core.spi.StreamingChatModel;
import io.quarkiverse.quarkai.openai.config.OpenAiConfig;
import io.quarkiverse.quarkai.openai.mapper.OpenAiErrorMapper;
import io.quarkiverse.quarkai.openai.mapper.OpenAiRequestMapper;
import io.quarkiverse.quarkai.openai.mapper.OpenAiResponseMapper;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.concurrent.TimeUnit;

/**
 * OpenAI provider implementation of {@link ChatModel} and {@link StreamingChatModel}.
 *
 * <p>Uses Vert.x {@link WebClient} for non-blocking HTTP calls. Registered as an
 * {@link ApplicationScoped} CDI bean — providers inject this when the OpenAI provider
 * is active.
 *
 * <p>Streaming uses SSE (Server-Sent Events) parsing of the {@code data:} lines
 * emitted by the {@code /v1/chat/completions?stream=true} endpoint.
 */
@ApplicationScoped
public class OpenAiChatModel implements ChatModel, StreamingChatModel {

    private static final String CHAT_PATH = "/chat/completions";
    private static final String CONTENT_TYPE = "application/json";

    @Inject
    OpenAiConfig config;

    @Inject
    Vertx vertx;

    private WebClient webClient;

    @PostConstruct
    void init() {
        WebClientOptions options = new WebClientOptions()
                .setConnectTimeout((int) TimeUnit.SECONDS.toMillis(config.timeoutSeconds()))
                .setIdleTimeout((int) config.timeoutSeconds())
                .setIdleTimeoutUnit(TimeUnit.SECONDS)
                .setKeepAlive(true)
                .setSsl(config.baseUrl().startsWith("https"));

        this.webClient = WebClient.create(vertx, options);
    }

    @PreDestroy
    void destroy() {
        if (webClient != null) {
            webClient.close();
        }
    }

    // ── ChatModel ─────────────────────────────────────────────────────────────

    @Override
    public Uni<AiResponse> chat(AiRequest request) {
        String body = OpenAiRequestMapper.toJson(request, false);

        var httpRequest = webClient.postAbs(config.baseUrl() + CHAT_PATH)
                .putHeader("Authorization", "Bearer " + config.apiKey())
                .putHeader("Content-Type", CONTENT_TYPE)
                .putHeader("Accept", CONTENT_TYPE);

        config.organizationId().ifPresent(
                org -> httpRequest.putHeader("OpenAI-Organization", org));

        return Uni.createFrom().completionStage(
                httpRequest.sendBuffer(Buffer.buffer(body)).toCompletionStage()
        ).map(this::handleResponse);
    }

    // ── StreamingChatModel ────────────────────────────────────────────────────

    @Override
    public Multi<AiResponse> stream(AiRequest request) {
        String body = OpenAiRequestMapper.toJson(request, true);

        return Multi.createFrom().emitter(emitter -> {
            webClient.postAbs(config.baseUrl() + CHAT_PATH)
                    .putHeader("Authorization", "Bearer " + config.apiKey())
                    .putHeader("Content-Type", CONTENT_TYPE)
                    .putHeader("Accept", "text/event-stream")
                    .sendBuffer(Buffer.buffer(body), ar -> {
                        if (ar.failed()) {
                            emitter.fail(new AiException("OpenAI stream request failed", ar.cause()));
                            return;
                        }
                        HttpResponse<Buffer> response = ar.result();
                        if (response.statusCode() != 200) {
                            emitter.fail(OpenAiErrorMapper.map(
                                    response.statusCode(), response.bodyAsString()));
                            return;
                        }
                        // Parse SSE body line-by-line
                        String[] lines = response.bodyAsString().split("\n");
                        for (String line : lines) {
                            if (line.startsWith("data:")) {
                                String data = line.substring(5).trim();
                                AiResponse chunk = OpenAiResponseMapper.fromStreamLine(data);
                                emitter.emit(chunk);
                                if (chunk.isFinished()) {
                                    emitter.complete();
                                    return;
                                }
                            }
                        }
                        emitter.complete();
                    });
        });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private AiResponse handleResponse(HttpResponse<Buffer> response) {
        if (response.statusCode() != 200) {
            throw OpenAiErrorMapper.map(response.statusCode(), response.bodyAsString());
        }
        return OpenAiResponseMapper.fromJson(response.bodyAsString());
    }
}
