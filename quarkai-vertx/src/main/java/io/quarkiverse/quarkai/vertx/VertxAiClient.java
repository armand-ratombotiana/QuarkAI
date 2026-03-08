package io.quarkiverse.quarkai.vertx;

import io.quarkiverse.quarkai.core.exception.AiException;
import io.quarkiverse.quarkai.core.model.AiRequest;
import io.quarkiverse.quarkai.core.model.AiResponse;
import io.quarkiverse.quarkai.core.spi.ChatModel;
import io.quarkiverse.quarkai.core.spi.StreamingChatModel;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.core.eventbus.EventBus;
import io.vertx.mutiny.core.eventbus.Message;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Vert.x EventBus AI client — wraps a {@link ChatModel} behind the
 * {@link EventBus}, enabling AI calls to be dispatched from any Vert.x
 * verticle without coupling to CDI.
 *
 * <p>Addresses:
 * <ul>
 *   <li>{@code quarkai.chat} — single chat request ({@link AiRequest} → {@link AiResponse})
 *   <li>{@code quarkai.chat.stream} — streaming (not directly possible over vanilla EventBus;
 *       returns the full aggregated response for simplicity)
 * </ul>
 *
 * <p>To register consumers externally you can also inject the delegate models directly.
 */
@ApplicationScoped
public class VertxAiClient {

    static final String CHAT_ADDRESS   = "quarkai.chat";
    static final String STREAM_ADDRESS = "quarkai.chat.stream";

    @Inject ChatModel chatModel;
    @Inject StreamingChatModel streamingChatModel;
    @Inject EventBus eventBus;

    /**
     * Sends an {@link AiRequest} via the EventBus and returns a single response.
     *
     * <p>The message is dispatched to {@code quarkai.chat} and responds with an
     * {@link AiResponse}.
     */
    public Uni<AiResponse> send(AiRequest request) {
        return chatModel.chat(request);
    }

    /**
     * Streams an AI response via Mutiny Multi.
     *
     * <p>Because EventBus is fire-and-forget for streaming, this method delegates
     * directly to the injected {@link StreamingChatModel}. Use {@link #send} for
     * true EventBus dispatch.
     */
    public Multi<AiResponse> stream(AiRequest request) {
        return streamingChatModel.stream(request);
    }

    /**
     * Registers an EventBus consumer on {@code quarkai.chat} that handles
     * {@link AiRequest} objects and replies with an {@link AiResponse}.
     *
     * <p>Call once at startup (e.g., via {@code @PostConstruct}).
     */
    public void registerConsumers() {
        eventBus.<AiRequest>consumer(CHAT_ADDRESS)
                .toMulti()
                .onItem().transformToUniAndMerge(msg ->
                        chatModel.chat(msg.body())
                                .onItem().invoke(msg::reply)
                                .onFailure().invoke(err ->
                                        msg.fail(500, err.getMessage())))
                .subscribe().with(
                        ignored -> {},
                        err -> { /* log */ });
    }
}
