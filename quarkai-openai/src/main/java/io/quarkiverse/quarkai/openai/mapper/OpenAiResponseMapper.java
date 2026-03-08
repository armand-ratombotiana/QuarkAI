package io.quarkiverse.quarkai.openai.mapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkiverse.quarkai.core.model.AiResponse;
import io.quarkiverse.quarkai.core.model.TokenUsage;

/**
 * Maps OpenAI Chat Completions API JSON responses to {@link AiResponse}.
 *
 * <p>Handles both batched (full) and streaming (delta) response formats.
 */
public final class OpenAiResponseMapper {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private OpenAiResponseMapper() {}

    /**
     * Maps a full (non-streaming) OpenAI response JSON string to {@link AiResponse}.
     */
    public static AiResponse fromJson(String json) {
        try {
            JsonNode root    = MAPPER.readTree(json);
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                return AiResponse.builder().content("").build();
            }

            JsonNode choice  = choices.get(0);
            String content   = choice.path("message").path("content").asText("");
            String finishStr = choice.path("finish_reason").asText("null");
            AiResponse.FinishReason reason = parseFinishReason(finishStr);

            TokenUsage usage = null;
            JsonNode usageNode = root.path("usage");
            if (!usageNode.isMissingNode()) {
                usage = TokenUsage.of(
                        usageNode.path("prompt_tokens").asInt(0),
                        usageNode.path("completion_tokens").asInt(0),
                        usageNode.path("total_tokens").asInt(0));
            }

            return AiResponse.builder()
                    .content(content)
                    .finishReason(reason)
                    .usage(usage)
                    .model(root.path("model").asText(null))
                    .id(root.path("id").asText(null))
                    .build();

        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse OpenAI response: " + e.getMessage(), e);
        }
    }

    /**
     * Maps a single SSE data line from an OpenAI streaming response to {@link AiResponse}.
     *
     * @param dataLine the {@code data: {...}} line content (without the "data: " prefix)
     */
    public static AiResponse fromStreamLine(String dataLine) {
        if ("[DONE]".equals(dataLine.trim())) {
            return AiResponse.builder()
                    .content("")
                    .finishReason(AiResponse.FinishReason.STOP)
                    .build();
        }
        try {
            JsonNode root    = MAPPER.readTree(dataLine);
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                return AiResponse.builder().content("").build();
            }

            JsonNode choice  = choices.get(0);
            String delta     = choice.path("delta").path("content").asText("");
            String finishStr = choice.path("finish_reason").asText("null");
            AiResponse.FinishReason reason = parseFinishReason(finishStr);

            return AiResponse.builder()
                    .content(delta)
                    .finishReason(reason)
                    .model(root.path("model").asText(null))
                    .id(root.path("id").asText(null))
                    .build();

        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse OpenAI stream line: " + e.getMessage(), e);
        }
    }

    private static AiResponse.FinishReason parseFinishReason(String raw) {
        return switch (raw) {
            case "stop"          -> AiResponse.FinishReason.STOP;
            case "length"        -> AiResponse.FinishReason.LENGTH;
            case "content_filter"-> AiResponse.FinishReason.CONTENT_FILTER;
            case "tool_calls"    -> AiResponse.FinishReason.TOOL_CALLS;
            default              -> AiResponse.FinishReason.NULL;
        };
    }
}
