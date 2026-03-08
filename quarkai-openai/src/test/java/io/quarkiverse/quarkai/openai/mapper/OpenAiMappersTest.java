package io.quarkiverse.quarkai.openai.mapper;

import io.quarkiverse.quarkai.core.exception.AiAuthException;
import io.quarkiverse.quarkai.core.exception.AiRateLimitException;
import io.quarkiverse.quarkai.core.model.AiRequest;
import io.quarkiverse.quarkai.core.model.AiResponse;
import io.quarkiverse.quarkai.core.model.Message;
import io.quarkiverse.quarkai.core.model.TokenUsage;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class OpenAiResponseMapperTest {

    @Test
    void fromJson_mapsAllFields() {
        String json = """
                {
                  "id": "chatcmpl-abc123",
                  "model": "gpt-4o",
                  "choices": [{
                    "message": {"role": "assistant", "content": "Hello!"},
                    "finish_reason": "stop"
                  }],
                  "usage": {
                    "prompt_tokens": 10,
                    "completion_tokens": 5,
                    "total_tokens": 15
                  }
                }
                """;

        AiResponse response = OpenAiResponseMapper.fromJson(json);

        assertThat(response.content()).isEqualTo("Hello!");
        assertThat(response.finishReason()).isEqualTo(AiResponse.FinishReason.STOP);
        assertThat(response.id()).contains("chatcmpl-abc123");
        assertThat(response.model()).contains("gpt-4o");
        assertThat(response.usage()).isPresent();
        assertThat(response.usage().get().promptTokens()).isEqualTo(10);
        assertThat(response.usage().get().completionTokens()).isEqualTo(5);
        assertThat(response.usage().get().totalTokens()).isEqualTo(15);
        assertThat(response.isFinished()).isTrue();
    }

    @Test
    void fromJson_lengthFinishReason() {
        String json = """
                {
                  "choices": [{
                    "message": {"role": "assistant", "content": "Truncated..."},
                    "finish_reason": "length"
                  }]
                }
                """;
        AiResponse response = OpenAiResponseMapper.fromJson(json);
        assertThat(response.finishReason()).isEqualTo(AiResponse.FinishReason.LENGTH);
    }

    @Test
    void fromStreamLine_done_returnsStopChunk() {
        AiResponse response = OpenAiResponseMapper.fromStreamLine("[DONE]");
        assertThat(response.finishReason()).isEqualTo(AiResponse.FinishReason.STOP);
        assertThat(response.isFinished()).isTrue();
    }

    @Test
    void fromStreamLine_deltaChunk() {
        String data = """
                {"id":"1","model":"gpt-4o","choices":[{"delta":{"content":"Hello"},"finish_reason":null}]}
                """;
        AiResponse response = OpenAiResponseMapper.fromStreamLine(data);
        assertThat(response.content()).isEqualTo("Hello");
        assertThat(response.isFinished()).isFalse();
    }
}

class OpenAiErrorMapperTest {

    @Test
    void maps401_toAuthException() {
        var ex = OpenAiErrorMapper.map(401, "Unauthorized");
        assertThat(ex).isInstanceOf(AiAuthException.class);
        assertThat(ex.statusCode()).isEqualTo(401);
    }

    @Test
    void maps429_toRateLimitException() {
        var ex = OpenAiErrorMapper.map(429, "Too Many Requests");
        assertThat(ex).isInstanceOf(AiRateLimitException.class);
        assertThat(ex.statusCode()).isEqualTo(429);
    }

    @Test
    void mapsUnknown_toBaseException() {
        var ex = OpenAiErrorMapper.map(500, "Internal Server Error");
        assertThat(ex.statusCode()).isEqualTo(500);
    }
}

class OpenAiRequestMapperTest {

    @Test
    void toJson_containsAllFields() {
        AiRequest request = AiRequest.builder()
                .model("gpt-4o")
                .addMessage(Message.system("You are an assistant."))
                .addMessage(Message.user("What is 2+2?"))
                .temperature(0.5)
                .maxTokens(100)
                .userId("u1")
                .build();

        String json = OpenAiRequestMapper.toJson(request, false);

        assertThat(json).contains("\"model\":\"gpt-4o\"");
        assertThat(json).contains("\"temperature\":0.5");
        assertThat(json).contains("\"max_tokens\":100");
        assertThat(json).contains("\"stream\":false");
        assertThat(json).contains("\"role\":\"system\"");
        assertThat(json).contains("\"role\":\"user\"");
        assertThat(json).contains("\"user\":\"u1\"");
    }

    @Test
    void toJson_streamingFlag() {
        AiRequest request = AiRequest.builder()
                .model("gpt-4o")
                .addMessage(Message.user("Hi"))
                .build();
        String json = OpenAiRequestMapper.toJson(request, true);
        assertThat(json).contains("\"stream\":true");
    }
}
