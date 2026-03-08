package io.quarkiverse.quarkai.core.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class AiResponseTest {

    @Test
    void builder_defaultFinishReasonIsNull() {
        AiResponse response = AiResponse.builder().content("Hello!").build();
        assertThat(response.finishReason()).isEqualTo(AiResponse.FinishReason.NULL);
        assertThat(response.isFinished()).isFalse();
    }

    @Test
    void builder_withAllFields() {
        TokenUsage usage = TokenUsage.of(10, 20);
        AiResponse response = AiResponse.builder()
                .content("The capital of France is Paris.")
                .finishReason(AiResponse.FinishReason.STOP)
                .usage(usage)
                .model("gpt-4o")
                .id("chatcmpl-abc123")
                .build();

        assertThat(response.content()).isEqualTo("The capital of France is Paris.");
        assertThat(response.finishReason()).isEqualTo(AiResponse.FinishReason.STOP);
        assertThat(response.usage()).contains(usage);
        assertThat(response.model()).contains("gpt-4o");
        assertThat(response.id()).contains("chatcmpl-abc123");
        assertThat(response.isFinished()).isTrue();
    }

    @Test
    void nullContentDefaultsToEmpty() {
        AiResponse response = AiResponse.builder().build();
        assertThat(response.content()).isEmpty();
    }

    @Test
    void usageAbsentWhenNotSet() {
        AiResponse response = AiResponse.builder().content("Hi").build();
        assertThat(response.usage()).isEmpty();
        assertThat(response.model()).isEmpty();
        assertThat(response.id()).isEmpty();
    }

    @Test
    void equalsAndHashCode() {
        AiResponse r1 = AiResponse.builder()
                .content("Hi").finishReason(AiResponse.FinishReason.STOP).build();
        AiResponse r2 = AiResponse.builder()
                .content("Hi").finishReason(AiResponse.FinishReason.STOP).build();
        assertThat(r1).isEqualTo(r2);
        assertThat(r1.hashCode()).isEqualTo(r2.hashCode());
    }
}
