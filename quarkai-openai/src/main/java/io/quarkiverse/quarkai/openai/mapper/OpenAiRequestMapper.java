package io.quarkiverse.quarkai.openai.mapper;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkiverse.quarkai.core.model.AiRequest;
import io.quarkiverse.quarkai.core.model.Message;

/**
 * Maps a provider-agnostic {@link AiRequest} to the OpenAI Chat Completions
 * API request body (JSON).
 */
public final class OpenAiRequestMapper {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private OpenAiRequestMapper() {}

    /**
     * Converts an {@link AiRequest} to an OpenAI-compatible JSON body string.
     *
     * @param request the provider-agnostic request
     * @param stream  whether to request SSE streaming from OpenAI
     * @return JSON string ready to be sent as the HTTP body
     */
    public static String toJson(AiRequest request, boolean stream) {
        ObjectNode root = MAPPER.createObjectNode();
        root.put("model", request.model());
        root.put("temperature", request.temperature());
        root.put("max_tokens", request.maxTokens());
        root.put("stream", stream);

        ArrayNode messages = root.putArray("messages");
        for (Message msg : request.messages()) {
            ObjectNode msgNode = messages.addObject();
            msgNode.put("role", roleName(msg.role()));
            msgNode.put("content", msg.content());
        }

        request.userId().ifPresent(uid -> root.put("user", uid));

        try {
            return MAPPER.writeValueAsString(root);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize OpenAI request", e);
        }
    }

    private static String roleName(Message.Role role) {
        return switch (role) {
            case SYSTEM    -> "system";
            case USER      -> "user";
            case ASSISTANT -> "assistant";
            case TOOL      -> "tool";
        };
    }
}
