package io.quarkiverse.quarkai.openai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.quarkiverse.quarkai.core.model.EmbeddingResult;
import io.quarkiverse.quarkai.core.model.TokenUsage;
import io.quarkiverse.quarkai.core.spi.EmbeddingModel;
import io.quarkiverse.quarkai.openai.config.OpenAiConfig;
import io.quarkiverse.quarkai.openai.mapper.OpenAiErrorMapper;
import io.smallrye.mutiny.Uni;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * OpenAI provider implementation of {@link EmbeddingModel}.
 * Calls the {@code /v1/embeddings} endpoint.
 */
@ApplicationScoped
public class OpenAiEmbeddingModel implements EmbeddingModel {

    private static final String EMBEDDINGS_PATH = "/embeddings";
    private static final String CONTENT_TYPE    = "application/json";
    private static final ObjectMapper MAPPER     = new ObjectMapper();

    @Inject OpenAiConfig config;
    @Inject Vertx vertx;

    private WebClient webClient;

    @PostConstruct
    void init() {
        webClient = WebClient.create(vertx, new WebClientOptions()
                .setConnectTimeout((int) TimeUnit.SECONDS.toMillis(config.timeoutSeconds()))
                .setSsl(config.baseUrl().startsWith("https")));
    }

    @PreDestroy
    void destroy() {
        if (webClient != null) webClient.close();
    }

    @Override
    public Uni<EmbeddingResult> embed(List<String> texts) {
        String body = buildRequestBody(texts);

        return Uni.createFrom().completionStage(
                webClient.postAbs(config.baseUrl() + EMBEDDINGS_PATH)
                        .putHeader("Authorization", "Bearer " + config.apiKey())
                        .putHeader("Content-Type", CONTENT_TYPE)
                        .sendBuffer(Buffer.buffer(body))
                        .toCompletionStage()
        ).map(response -> {
            if (response.statusCode() != 200) {
                throw OpenAiErrorMapper.map(response.statusCode(), response.bodyAsString());
            }
            return parseResponse(response.bodyAsString());
        });
    }

    private String buildRequestBody(List<String> texts) {
        try {
            ObjectNode root = MAPPER.createObjectNode();
            root.put("model", "text-embedding-3-small");
            ArrayNode input = root.putArray("input");
            texts.forEach(input::add);
            return MAPPER.writeValueAsString(root);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build embedding request", e);
        }
    }

    private EmbeddingResult parseResponse(String json) {
        try {
            JsonNode root = MAPPER.readTree(json);
            JsonNode data = root.path("data");
            List<float[]> embeddings = new ArrayList<>();
            for (JsonNode item : data) {
                JsonNode embNode = item.path("embedding");
                float[] vec = new float[embNode.size()];
                for (int i = 0; i < vec.length; i++) {
                    vec[i] = (float) embNode.get(i).asDouble();
                }
                embeddings.add(vec);
            }
            JsonNode usageNode = root.path("usage");
            TokenUsage usage = TokenUsage.of(
                    usageNode.path("prompt_tokens").asInt(0), 0);
            return EmbeddingResult.of(embeddings, root.path("model").asText(null), usage);
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse embedding response", e);
        }
    }
}
