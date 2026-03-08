package io.quarkiverse.quarkai.ollama;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.quarkiverse.quarkai.core.exception.AiException;
import io.quarkiverse.quarkai.core.model.AiRequest;
import io.quarkiverse.quarkai.core.model.AiResponse;
import io.quarkiverse.quarkai.core.model.Message;
import io.quarkiverse.quarkai.core.model.TokenUsage;
import io.quarkiverse.quarkai.core.spi.ChatModel;
import io.quarkiverse.quarkai.core.spi.StreamingChatModel;
import io.quarkiverse.quarkai.ollama.config.OllamaConfig;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.concurrent.TimeUnit;

/**
 * Ollama local model provider — targets {@code /api/chat} (OpenAI-compatible messages format).
 *
 * <p>Streaming uses newline-delimited JSON (NDJSON) returned by Ollama.
 */
@ApplicationScoped
public class OllamaChatModel implements ChatModel, StreamingChatModel {

    private static final String CHAT_PATH = "/api/chat";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Inject OllamaConfig config;
    @Inject Vertx vertx;

    private WebClient webClient;

    @PostConstruct
    void init() {
        webClient = WebClient.create(vertx, new WebClientOptions()
                .setConnectTimeout((int) TimeUnit.SECONDS.toMillis(config.timeoutSeconds()))
                .setIdleTimeout((int) config.timeoutSeconds())
                .setIdleTimeoutUnit(TimeUnit.SECONDS));
    }

    @PreDestroy
    void destroy() {
        if (webClient != null) webClient.close();
    }

    @Override
    public Uni<AiResponse> chat(AiRequest request) {
        String body = buildBody(request, false);
        return Uni.createFrom().completionStage(
                webClient.postAbs(config.baseUrl() + CHAT_PATH)
                        .putHeader("Content-Type", "application/json")
                        .sendBuffer(Buffer.buffer(body))
                        .toCompletionStage()
        ).map(response -> {
            if (response.statusCode() != 200) {
                throw new AiException("Ollama error " + response.statusCode()
                        + ": " + response.bodyAsString(), response.statusCode());
            }
            return parseResponse(response.bodyAsString());
        });
    }

    @Override
    public Multi<AiResponse> stream(AiRequest request) {
        String body = buildBody(request, true);
        return Multi.createFrom().emitter(emitter -> {
            webClient.postAbs(config.baseUrl() + CHAT_PATH)
                    .putHeader("Content-Type", "application/json")
                    .sendBuffer(Buffer.buffer(body), ar -> {
                        if (ar.failed()) {
                            emitter.fail(new AiException("Ollama stream failed", ar.cause()));
                            return;
                        }
                        if (ar.result().statusCode() != 200) {
                            emitter.fail(new AiException("Ollama stream error " + ar.result().statusCode(),
                                    ar.result().statusCode()));
                            return;
                        }
                        // NDJSON: each line is a JSON object
                        String[] lines = ar.result().bodyAsString().split("\n");
                        for (String line : lines) {
                            if (line.isBlank()) continue;
                            try {
                                JsonNode node    = MAPPER.readTree(line);
                                String content   = node.path("message").path("content").asText("");
                                boolean done     = node.path("done").asBoolean(false);
                                AiResponse.FinishReason reason = done
                                        ? AiResponse.FinishReason.STOP
                                        : AiResponse.FinishReason.NULL;
                                emitter.emit(AiResponse.builder()
                                        .content(content).finishReason(reason).build());
                                if (done) { emitter.complete(); return; }
                            } catch (Exception ignored) {}
                        }
                        emitter.complete();
                    });
        });
    }

    private String buildBody(AiRequest request, boolean stream) {
        try {
            ObjectNode root = MAPPER.createObjectNode();
            root.put("model", request.model());
            root.put("stream", stream);
            ArrayNode messages = root.putArray("messages");
            for (Message msg : request.messages()) {
                ObjectNode m = messages.addObject();
                m.put("role", switch (msg.role()) {
                    case SYSTEM    -> "system";
                    case USER      -> "user";
                    case ASSISTANT -> "assistant";
                    case TOOL      -> "tool";
                });
                m.put("content", msg.content());
            }
            // Options
            ObjectNode options = root.putObject("options");
            options.put("temperature", request.temperature());
            options.put("num_predict", request.maxTokens());
            return MAPPER.writeValueAsString(root);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build Ollama request", e);
        }
    }

    private AiResponse parseResponse(String json) {
        try {
            JsonNode root  = MAPPER.readTree(json);
            String content = root.path("message").path("content").asText("");
            boolean done   = root.path("done").asBoolean(true);
            TokenUsage usage = TokenUsage.of(
                    root.path("prompt_eval_count").asInt(0),
                    root.path("eval_count").asInt(0));
            return AiResponse.builder()
                    .content(content)
                    .finishReason(done ? AiResponse.FinishReason.STOP : AiResponse.FinishReason.NULL)
                    .usage(usage)
                    .model(root.path("model").asText(null))
                    .build();
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse Ollama response", e);
        }
    }
}
