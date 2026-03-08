package io.quarkiverse.quarkai.anthropic;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.quarkiverse.quarkai.anthropic.config.AnthropicConfig;
import io.quarkiverse.quarkai.core.exception.AiAuthException;
import io.quarkiverse.quarkai.core.exception.AiException;
import io.quarkiverse.quarkai.core.exception.AiRateLimitException;
import io.quarkiverse.quarkai.core.model.AiRequest;
import io.quarkiverse.quarkai.core.model.AiResponse;
import io.quarkiverse.quarkai.core.model.Message;
import io.quarkiverse.quarkai.core.model.TokenUsage;
import io.quarkiverse.quarkai.core.spi.ChatModel;
import io.quarkiverse.quarkai.core.spi.StreamingChatModel;
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
 * Anthropic Claude provider implementation — supports the Messages API.
 *
 * <p>Differences from OpenAI protocol:
 * <ul>
 *   <li>System messages are hoisted to a top-level {@code system} field
 *   <li>Auth via {@code x-api-key} header, not {@code Authorization: Bearer}
 *   <li>API version pinned via {@code anthropic-version} header
 * </ul>
 */
@ApplicationScoped
public class AnthropicChatModel implements ChatModel, StreamingChatModel {

    private static final String MESSAGES_PATH = "/messages";
    private static final String CONTENT_TYPE  = "application/json";
    private static final ObjectMapper MAPPER   = new ObjectMapper();

    @Inject AnthropicConfig config;
    @Inject Vertx vertx;

    private WebClient webClient;

    @PostConstruct
    void init() {
        webClient = WebClient.create(vertx, new WebClientOptions()
                .setConnectTimeout((int) TimeUnit.SECONDS.toMillis(config.timeoutSeconds()))
                .setIdleTimeout((int) config.timeoutSeconds())
                .setIdleTimeoutUnit(TimeUnit.SECONDS)
                .setSsl(config.baseUrl().startsWith("https")));
    }

    @PreDestroy
    void destroy() {
        if (webClient != null) webClient.close();
    }

    // ── ChatModel ─────────────────────────────────────────────────────────────

    @Override
    public Uni<AiResponse> chat(AiRequest request) {
        String body = buildRequestBody(request, false);
        return Uni.createFrom().completionStage(
                webClient.postAbs(config.baseUrl() + MESSAGES_PATH)
                        .putHeader("x-api-key", config.apiKey())
                        .putHeader("anthropic-version", config.apiVersion())
                        .putHeader("Content-Type", CONTENT_TYPE)
                        .sendBuffer(Buffer.buffer(body))
                        .toCompletionStage()
        ).map(response -> {
            if (response.statusCode() != 200) {
                throw mapError(response.statusCode(), response.bodyAsString());
            }
            return parseResponse(response.bodyAsString());
        });
    }

    // ── StreamingChatModel ────────────────────────────────────────────────────

    @Override
    public Multi<AiResponse> stream(AiRequest request) {
        String body = buildRequestBody(request, true);
        return Multi.createFrom().emitter(emitter -> {
            webClient.postAbs(config.baseUrl() + MESSAGES_PATH)
                    .putHeader("x-api-key", config.apiKey())
                    .putHeader("anthropic-version", config.apiVersion())
                    .putHeader("Content-Type", CONTENT_TYPE)
                    .putHeader("Accept", "text/event-stream")
                    .sendBuffer(Buffer.buffer(body), ar -> {
                        if (ar.failed()) {
                            emitter.fail(new AiException("Anthropic stream failed", ar.cause()));
                            return;
                        }
                        if (ar.result().statusCode() != 200) {
                            emitter.fail(mapError(ar.result().statusCode(), ar.result().bodyAsString()));
                            return;
                        }
                        String[] lines = ar.result().bodyAsString().split("\n");
                        for (String line : lines) {
                            if (line.startsWith("data:")) {
                                String data = line.substring(5).trim();
                                try {
                                    JsonNode node = MAPPER.readTree(data);
                                    String type = node.path("type").asText("");
                                    if ("content_block_delta".equals(type)) {
                                        String text = node.path("delta").path("text").asText("");
                                        emitter.emit(AiResponse.builder().content(text).build());
                                    } else if ("message_stop".equals(type)) {
                                        emitter.emit(AiResponse.builder()
                                                .content("")
                                                .finishReason(AiResponse.FinishReason.STOP)
                                                .build());
                                        emitter.complete();
                                        return;
                                    }
                                } catch (Exception e) {
                                    // skip unparseable lines
                                }
                            }
                        }
                        emitter.complete();
                    });
        });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String buildRequestBody(AiRequest request, boolean stream) {
        try {
            ObjectNode root = MAPPER.createObjectNode();
            root.put("model", request.model());
            root.put("max_tokens", request.maxTokens());
            root.put("stream", stream);

            // Anthropic separates system from messages
            StringBuilder systemContent = new StringBuilder();
            ArrayNode messages = root.putArray("messages");
            for (Message msg : request.messages()) {
                if (msg.role() == Message.Role.SYSTEM) {
                    if (!systemContent.isEmpty()) systemContent.append("\n");
                    systemContent.append(msg.content());
                } else {
                    ObjectNode m = messages.addObject();
                    m.put("role", msg.role() == Message.Role.USER ? "user" : "assistant");
                    m.put("content", msg.content());
                }
            }
            if (!systemContent.isEmpty()) {
                root.put("system", systemContent.toString());
            }
            return MAPPER.writeValueAsString(root);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build Anthropic request", e);
        }
    }

    private AiResponse parseResponse(String json) {
        try {
            JsonNode root    = MAPPER.readTree(json);
            JsonNode content = root.path("content");
            String text = content.isArray() && !content.isEmpty()
                    ? content.get(0).path("text").asText("")
                    : "";
            String stopReason = root.path("stop_reason").asText("end_turn");

            AiResponse.FinishReason reason = switch (stopReason) {
                case "end_turn" -> AiResponse.FinishReason.STOP;
                case "max_tokens" -> AiResponse.FinishReason.LENGTH;
                default -> AiResponse.FinishReason.STOP;
            };

            JsonNode usage = root.path("usage");
            TokenUsage tokenUsage = TokenUsage.of(
                    usage.path("input_tokens").asInt(0),
                    usage.path("output_tokens").asInt(0));

            return AiResponse.builder()
                    .content(text)
                    .finishReason(reason)
                    .usage(tokenUsage)
                    .model(root.path("model").asText(null))
                    .id(root.path("id").asText(null))
                    .build();
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse Anthropic response", e);
        }
    }

    private AiException mapError(int status, String body) {
        return switch (status) {
            case 401 -> new AiAuthException("Anthropic auth failed. Body: " + body);
            case 429 -> new AiRateLimitException("Anthropic rate limit. Body: " + body);
            default  -> new AiException("Anthropic error " + status + ". Body: " + body, status);
        };
    }
}
