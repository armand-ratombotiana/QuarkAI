package io.quarkiverse.quarkai.vertex;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.quarkiverse.quarkai.core.exception.AiAuthException;
import io.quarkiverse.quarkai.core.exception.AiException;
import io.quarkiverse.quarkai.core.model.AiRequest;
import io.quarkiverse.quarkai.core.model.AiResponse;
import io.quarkiverse.quarkai.core.model.Message;
import io.quarkiverse.quarkai.core.model.TokenUsage;
import io.quarkiverse.quarkai.core.spi.ChatModel;
import io.quarkiverse.quarkai.core.spi.StreamingChatModel;
import io.quarkiverse.quarkai.vertex.config.VertexAiConfig;
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
 * Google Vertex AI (Gemini) provider implementation.
 *
 * <p>Calls the Vertex AI Generative Language API:
 * {@code https://{location}-aiplatform.googleapis.com/v1/projects/{project}/locations/{location}/publishers/google/models/{model}:generateContent}
 */
@ApplicationScoped
public class VertexAiChatModel implements ChatModel, StreamingChatModel {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Inject VertexAiConfig config;
    @Inject Vertx vertx;

    private WebClient webClient;
    String baseUrl; // package-private for testing

    @PostConstruct
    void init() {
        webClient = WebClient.create(vertx, new WebClientOptions()
                .setConnectTimeout((int) TimeUnit.SECONDS.toMillis(config.timeoutSeconds()))
                .setSsl(true));
        baseUrl = String.format("https://%s-aiplatform.googleapis.com/v1/projects/%s/locations/%s" +
                "/publishers/google/models/%s",
                config.location(), config.projectId(), config.location(), config.model());
    }

    @PreDestroy
    void destroy() {
        if (webClient != null) webClient.close();
    }

    @Override
    public Uni<AiResponse> chat(AiRequest request) {
        String body = buildBody(request);
        return Uni.createFrom().completionStage(
                webClient.postAbs(baseUrl + ":generateContent")
                        .putHeader("Authorization", "Bearer " + config.accessToken())
                        .putHeader("Content-Type", "application/json")
                        .sendBuffer(Buffer.buffer(body))
                        .toCompletionStage()
        ).map(response -> {
            if (response.statusCode() != 200) {
                throw mapError(response.statusCode(), response.bodyAsString());
            }
            return parseResponse(response.bodyAsString());
        });
    }

    @Override
    public Multi<AiResponse> stream(AiRequest request) {
        String body = buildBody(request);
        return Multi.createFrom().emitter(emitter -> {
            webClient.postAbs(baseUrl + ":streamGenerateContent")
                    .putHeader("Authorization", "Bearer " + config.accessToken())
                    .putHeader("Content-Type", "application/json")
                    .sendBuffer(Buffer.buffer(body), ar -> {
                        if (ar.failed()) {
                            emitter.fail(new AiException("Vertex AI stream failed", ar.cause()));
                            return;
                        }
                        if (ar.result().statusCode() != 200) {
                            emitter.fail(mapError(ar.result().statusCode(), ar.result().bodyAsString()));
                            return;
                        }
                        // Vertex streams a JSON array of GenerateContentResponse objects
                        try {
                            JsonNode array = MAPPER.readTree(ar.result().bodyAsString());
                            if (array.isArray()) {
                                for (JsonNode item : array) {
                                    emitter.emit(parseResponse(MAPPER.writeValueAsString(item)));
                                }
                            }
                        } catch (Exception e) {
                            emitter.fail(new AiException("Failed to parse Vertex stream", e));
                            return;
                        }
                        emitter.emit(AiResponse.builder()
                                .content("").finishReason(AiResponse.FinishReason.STOP).build());
                        emitter.complete();
                    });
        });
    }

    private String buildBody(AiRequest request) {
        try {
            ObjectNode root = MAPPER.createObjectNode();
            ArrayNode contents = root.putArray("contents");
            for (Message msg : request.messages()) {
                if (msg.role() == Message.Role.SYSTEM) continue; // system handled below
                ObjectNode content = contents.addObject();
                content.put("role", msg.role() == Message.Role.USER ? "user" : "model");
                content.putArray("parts").addObject().put("text", msg.content());
            }
            // System instruction
            request.messages().stream()
                    .filter(m -> m.role() == Message.Role.SYSTEM)
                    .findFirst()
                    .ifPresent(sys -> root.putObject("systemInstruction")
                            .putArray("parts").addObject().put("text", sys.content()));

            ObjectNode config = root.putObject("generationConfig");
            config.put("temperature", (float) request.temperature());
            config.put("maxOutputTokens", request.maxTokens());
            return MAPPER.writeValueAsString(root);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build Vertex AI body", e);
        }
    }

    private AiResponse parseResponse(String json) {
        try {
            JsonNode root        = MAPPER.readTree(json);
            JsonNode candidates  = root.path("candidates");
            String text          = candidates.isArray() && !candidates.isEmpty()
                    ? candidates.get(0).path("content").path("parts").get(0).path("text").asText("")
                    : "";
            String finishStr     = candidates.isArray() && !candidates.isEmpty()
                    ? candidates.get(0).path("finishReason").asText("STOP")
                    : "STOP";
            AiResponse.FinishReason reason = switch (finishStr) {
                case "STOP"       -> AiResponse.FinishReason.STOP;
                case "MAX_TOKENS" -> AiResponse.FinishReason.LENGTH;
                case "SAFETY"     -> AiResponse.FinishReason.CONTENT_FILTER;
                default           -> AiResponse.FinishReason.STOP;
            };
            JsonNode usage = root.path("usageMetadata");
            TokenUsage tokenUsage = TokenUsage.of(
                    usage.path("promptTokenCount").asInt(0),
                    usage.path("candidatesTokenCount").asInt(0));
            return AiResponse.builder()
                    .content(text).finishReason(reason).usage(tokenUsage).build();
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse Vertex AI response", e);
        }
    }

    private AiException mapError(int status, String body) {
        return switch (status) {
            case 401, 403 -> new AiAuthException("Vertex AI auth failed. Body: " + body);
            default       -> new AiException("Vertex AI error " + status, status);
        };
    }
}
