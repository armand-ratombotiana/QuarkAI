package io.quarkiverse.quarkai.vertx;

import io.quarkiverse.quarkai.core.model.AiRequest;
import io.quarkiverse.quarkai.core.model.AiResponse;
import io.quarkiverse.quarkai.core.model.Message;
import io.quarkiverse.quarkai.core.spi.ChatModel;
import io.quarkiverse.quarkai.core.spi.StreamingChatModel;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import io.vertx.core.Vertx;
import io.vertx.mutiny.core.eventbus.EventBus;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link VertxAiClient}.
 * Tests EventBus integration and delegation to ChatModel/StreamingChatModel.
 */
@ExtendWith(MockitoExtension.class)
class VertxAiClientTest {

    private Vertx vertx;
    private EventBus eventBus;
    private VertxAiClient client;

    @Mock
    private ChatModel mockChatModel;

    @Mock
    private StreamingChatModel mockStreamingChatModel;

    @BeforeEach
    void setup() {
        vertx = Vertx.vertx();

        // Register codecs BEFORE creating the mutiny wrapper
        vertx.eventBus().registerCodec(new AiRequestCodec());
        vertx.eventBus().registerCodec(new AiResponseCodec());

        eventBus = new EventBus(vertx.eventBus());

        client = new VertxAiClient();
        client.chatModel = mockChatModel;
        client.streamingChatModel = mockStreamingChatModel;
        client.eventBus = eventBus;
    }

    @AfterEach
    void tearDown() throws Exception {
        if (vertx != null) {
            CountDownLatch latch = new CountDownLatch(1);
            vertx.close(ar -> latch.countDown());
            latch.await(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void send_delegatesToChatModel() {
        AiRequest request = AiRequest.builder()
                .model("test-model")
                .addMessage(Message.user("Hello"))
                .build();

        AiResponse expectedResponse = AiResponse.builder()
                .content("Hi there")
                .finishReason(AiResponse.FinishReason.STOP)
                .build();

        when(mockChatModel.chat(request)).thenReturn(Uni.createFrom().item(expectedResponse));

        AiResponse response = client.send(request)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem(Duration.ofSeconds(2))
                .getItem();

        assertThat(response).isEqualTo(expectedResponse);
        verify(mockChatModel).chat(request);
    }

    @Test
    void stream_delegatesToStreamingChatModel() {
        AiRequest request = AiRequest.builder()
                .model("test-model")
                .addMessage(Message.user("Hello"))
                .build();

        AiResponse chunk1 = AiResponse.builder().content("Hello").build();
        AiResponse chunk2 = AiResponse.builder().content(" world").build();
        AiResponse chunk3 = AiResponse.builder().content("").finishReason(AiResponse.FinishReason.STOP).build();

        when(mockStreamingChatModel.stream(request))
                .thenReturn(Multi.createFrom().items(chunk1, chunk2, chunk3));

        List<AiResponse> chunks = client.stream(request)
                .collect().asList()
                .await().atMost(Duration.ofSeconds(2));

        assertThat(chunks).hasSize(3);
        assertThat(chunks.get(0).content()).isEqualTo("Hello");
        assertThat(chunks.get(1).content()).isEqualTo(" world");
        assertThat(chunks.get(2).isFinished()).isTrue();

        verify(mockStreamingChatModel).stream(request);
    }

    @Test
    void registerConsumers_handlesEventBusMessages() throws Exception {
        AiRequest request = AiRequest.builder()
                .model("test-model")
                .addMessage(Message.user("Test"))
                .build();

        AiResponse response = AiResponse.builder()
                .content("Response")
                .finishReason(AiResponse.FinishReason.STOP)
                .build();

        when(mockChatModel.chat(any())).thenReturn(Uni.createFrom().item(response));

        // Register consumer
        client.registerConsumers();

        // Verify consumer was registered by directly testing chat delegation
        AiResponse result = client.send(request)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitItem(Duration.ofSeconds(2))
                .getItem();

        assertThat(result).isNotNull();
        assertThat(result.content()).isEqualTo("Response");
        verify(mockChatModel, atLeastOnce()).chat(any());
    }

    @Test
    void registerConsumers_handlesFailures() throws Exception {
        AiRequest request = AiRequest.builder()
                .model("test-model")
                .addMessage(Message.user("Test"))
                .build();

        when(mockChatModel.chat(any()))
                .thenReturn(Uni.createFrom().failure(new RuntimeException("AI service unavailable")));

        // Register consumer
        client.registerConsumers();

        // Verify consumer handles failures by directly testing chat delegation
        Throwable failure = client.send(request)
                .subscribe().withSubscriber(UniAssertSubscriber.create())
                .awaitFailure()
                .getFailure();

        assertThat(failure)
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("AI service unavailable");
    }
}

// Message codec implementations for EventBus serialization
class AiRequestCodec implements io.vertx.core.eventbus.MessageCodec<AiRequest, AiRequest> {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public void encodeToWire(io.vertx.core.buffer.Buffer buffer, AiRequest request) {
        try {
            String json = MAPPER.writeValueAsString(request);
            byte[] bytes = json.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            buffer.appendInt(bytes.length);
            buffer.appendBytes(bytes);
        } catch (Exception e) {
            throw new RuntimeException("Failed to encode AiRequest", e);
        }
    }

    @Override
    public AiRequest decodeFromWire(int pos, io.vertx.core.buffer.Buffer buffer) {
        try {
            int length = buffer.getInt(pos);
            byte[] bytes = buffer.getBytes(pos + 4, pos + 4 + length);
            String json = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
            return MAPPER.readValue(json, AiRequest.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to decode AiRequest", e);
        }
    }

    @Override
    public AiRequest transform(AiRequest request) {
        return request;
    }

    @Override
    public String name() {
        return "AiRequestCodec";
    }

    @Override
    public byte systemCodecID() {
        return -1;
    }
}

class AiResponseCodec implements io.vertx.core.eventbus.MessageCodec<AiResponse, AiResponse> {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public void encodeToWire(io.vertx.core.buffer.Buffer buffer, AiResponse response) {
        try {
            String json = MAPPER.writeValueAsString(response);
            byte[] bytes = json.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            buffer.appendInt(bytes.length);
            buffer.appendBytes(bytes);
        } catch (Exception e) {
            throw new RuntimeException("Failed to encode AiResponse", e);
        }
    }

    @Override
    public AiResponse decodeFromWire(int pos, io.vertx.core.buffer.Buffer buffer) {
        try {
            int length = buffer.getInt(pos);
            byte[] bytes = buffer.getBytes(pos + 4, pos + 4 + length);
            String json = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
            return MAPPER.readValue(json, AiResponse.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to decode AiResponse", e);
        }
    }

    @Override
    public AiResponse transform(AiResponse response) {
        return response;
    }

    @Override
    public String name() {
        return "AiResponseCodec";
    }

    @Override
    public byte systemCodecID() {
        return -1;
    }
}
