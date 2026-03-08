package io.quarkiverse.quarkai.core.exception;

/**
 * Thrown when the AI provider rate-limits the request (HTTP 429).
 *
 * <p>Callers may inspect {@link #retryAfterSeconds()} when the provider
 * returns a {@code Retry-After} header.
 */
public class AiRateLimitException extends AiException {

    private final long retryAfterSeconds;

    public AiRateLimitException(String message) {
        super(message, 429);
        this.retryAfterSeconds = -1;
    }

    public AiRateLimitException(String message, long retryAfterSeconds) {
        super(message, 429);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    /**
     * Number of seconds to wait before retrying, or {@code -1} if unknown.
     */
    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
