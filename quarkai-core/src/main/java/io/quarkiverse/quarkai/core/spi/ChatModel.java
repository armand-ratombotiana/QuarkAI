package io.quarkiverse.quarkai.core.spi;

import io.quarkiverse.quarkai.core.model.AiRequest;
import io.quarkiverse.quarkai.core.model.AiResponse;
import io.smallrye.mutiny.Uni;

/**
 * Synchronous (single-response) chat model interface.
 *
 * <p>Implementations must be non-blocking; the returned {@link Uni} must
 * execute its HTTP call on an I/O thread and never park a caller thread.
 *
 * <p>Usage:
 * <pre>{@code
 * @Inject ChatModel chatModel;
 *
 * Uni<AiResponse> response = chatModel.chat(
 *     AiRequest.builder()
 *         .model("gpt-4o")
 *         .addMessage(Message.user("Hello!"))
 *         .build()
 * );
 * }</pre>
 */
public interface ChatModel extends AiModel {

    /**
     * Sends a chat request and returns a single response.
     *
     * @param request the AI request (non-null)
     * @return a {@link Uni} that emits one {@link AiResponse}
     */
    Uni<AiResponse> chat(AiRequest request);
}
