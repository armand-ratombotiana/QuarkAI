package io.quarkiverse.quarkai.core.spi;

import io.quarkiverse.quarkai.core.model.AiRequest;
import io.quarkiverse.quarkai.core.model.AiResponse;
import io.smallrye.mutiny.Multi;

/**
 * Streaming chat model interface that emits incremental response tokens.
 *
 * <p>Each emission is a partial {@link AiResponse}. The stream completes
 * when the provider signals a finish reason (e.g. {@code stop}).
 *
 * <p>Usage:
 * <pre>{@code
 * chatModel.stream(request)
 *     .subscribe().with(
 *         chunk -> System.out.print(chunk.content()),
 *         err   -> log.error("Stream error", err)
 *     );
 * }</pre>
 */
public interface StreamingChatModel extends AiModel {

    /**
     * Sends a chat request and returns a reactive stream of partial responses.
     *
     * @param request the AI request (non-null)
     * @return a {@link Multi} emitting incremental {@link AiResponse} chunks
     */
    Multi<AiResponse> stream(AiRequest request);
}
