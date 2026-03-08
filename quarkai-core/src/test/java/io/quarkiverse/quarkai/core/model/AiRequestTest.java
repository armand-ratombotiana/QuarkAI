package io.quarkiverse.quarkai.core.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

class AiRequestTest {

    @Test
    void builder_setsAllFields() {
        AiRequest request = AiRequest.builder()
                .model("gpt-4o")
                .addMessage(Message.system("You are a helpful assistant."))
                .addMessage(Message.user("Hello!"))
                .temperature(0.7)
                .maxTokens(512)
                .stream(true)
                .userId("user-123")
                .build();

        assertThat(request.model()).isEqualTo("gpt-4o");
        assertThat(request.messages()).hasSize(2);
        assertThat(request.messages().get(0).role()).isEqualTo(Message.Role.SYSTEM);
        assertThat(request.messages().get(1).role()).isEqualTo(Message.Role.USER);
        assertThat(request.temperature()).isEqualTo(0.7);
        assertThat(request.maxTokens()).isEqualTo(512);
        assertThat(request.stream()).isTrue();
        assertThat(request.userId()).contains("user-123");
    }

    @Test
    void builder_defaultValues() {
        AiRequest request = AiRequest.builder()
                .model("claude-3-haiku-20240307")
                .addMessage(Message.user("Test"))
                .build();

        assertThat(request.temperature()).isEqualTo(1.0);
        assertThat(request.maxTokens()).isEqualTo(1024);
        assertThat(request.stream()).isFalse();
        assertThat(request.userId()).isEmpty();
    }

    @Test
    void build_throwsWhenModelIsNull() {
        assertThatNullPointerException()
                .isThrownBy(() -> AiRequest.builder()
                        .addMessage(Message.user("Hi"))
                        .build())
                .withMessageContaining("model");
    }

    @Test
    void build_throwsWhenMessagesIsEmpty() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> AiRequest.builder()
                        .model("gpt-4o")
                        .build())
                .withMessageContaining("at least one message");
    }

    @Test
    void build_throwsOnInvalidTemperature() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> AiRequest.builder()
                        .model("gpt-4o")
                        .addMessage(Message.user("Hi"))
                        .temperature(3.0)
                        .build())
                .withMessageContaining("temperature");
    }

    @Test
    void build_throwsOnNonPositiveMaxTokens() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> AiRequest.builder()
                        .model("gpt-4o")
                        .addMessage(Message.user("Hi"))
                        .maxTokens(0)
                        .build())
                .withMessageContaining("maxTokens");
    }

    @Test
    void messages_areImmutable() {
        AiRequest request = AiRequest.builder()
                .model("gpt-4o")
                .addMessage(Message.user("Hi"))
                .build();

        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> request.messages().add(Message.user("Extra")));
    }

    @Test
    void equalsAndHashCode_areSymmetric() {
        AiRequest r1 = AiRequest.builder().model("gpt-4o").addMessage(Message.user("Hi")).build();
        AiRequest r2 = AiRequest.builder().model("gpt-4o").addMessage(Message.user("Hi")).build();

        assertThat(r1).isEqualTo(r2);
        assertThat(r1.hashCode()).isEqualTo(r2.hashCode());
    }

    @Test
    void messages_builderBulkReplace() {
        AiRequest request = AiRequest.builder()
                .model("gpt-4o")
                .addMessage(Message.user("original"))
                .messages(List.of(Message.system("sys"), Message.user("new")))
                .build();

        assertThat(request.messages()).hasSize(2);
        assertThat(request.messages().get(0).role()).isEqualTo(Message.Role.SYSTEM);
    }
}
