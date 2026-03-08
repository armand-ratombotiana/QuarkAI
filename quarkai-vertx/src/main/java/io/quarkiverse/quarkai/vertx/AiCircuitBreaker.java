package io.quarkiverse.quarkai.vertx;

import io.quarkiverse.quarkai.core.exception.AiException;
import io.quarkiverse.quarkai.core.model.AiRequest;
import io.quarkiverse.quarkai.core.model.AiResponse;
import io.quarkiverse.quarkai.core.spi.ChatModel;
import io.smallrye.mutiny.Uni;
import io.vertx.circuitbreaker.CircuitBreaker;
import io.vertx.circuitbreaker.CircuitBreakerOptions;
import io.vertx.core.Vertx;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Duration;

/**
 * Circuit-breaker wrapper around the active {@link ChatModel}.
 *
 * <p>Prevents cascading failures when AI providers are unavailable.
 * Falls back to a {@link AiException} after configured failure threshold.
 *
 * <p>Usage:
 * <pre>{@code
 * @Inject AiCircuitBreaker breaker;
 *
 * breaker.chat(request)
 *     .onFailure(AiException.class).recoverWithItem(fallbackResponse)
 *     .subscribe().with(...);
 * }</pre>
 */
@ApplicationScoped
public class AiCircuitBreaker {

    private static final int    FAILURE_THRESHOLD  = 5;
    private static final long   RESET_TIMEOUT_MS   = 10_000L;
    private static final long   REQUEST_TIMEOUT_MS = 30_000L;

    @Inject ChatModel chatModel;
    @Inject Vertx vertx;

    private CircuitBreaker breaker;

    @PostConstruct
    void init() {
        CircuitBreakerOptions options = new CircuitBreakerOptions()
                .setMaxFailures(FAILURE_THRESHOLD)
                .setTimeout(REQUEST_TIMEOUT_MS)
                .setResetTimeout(RESET_TIMEOUT_MS);

        breaker = CircuitBreaker.create("quarkai-ai-breaker", vertx, options);
    }

    /**
     * Executes a chat request protected by the circuit breaker.
     *
     * @param request the AI request
     * @return a {@link Uni} that fails fast when the circuit is open
     */
    public Uni<AiResponse> chat(AiRequest request) {
        return Uni.createFrom().<AiResponse>completionStage(
                breaker.<AiResponse>execute(promise ->
                        chatModel.chat(request)
                                .subscribe().with(
                                        promise::complete,
                                        promise::fail))
                        .toCompletionStage()
        );
    }
}
