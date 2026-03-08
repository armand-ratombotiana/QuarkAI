package io.quarkiverse.quarkai.core.exception;

/**
 * Base unchecked exception for all QuarkAI errors.
 *
 * <p>All provider-specific errors are mapped to a subclass of this exception,
 * allowing callers to handle them in a provider-agnostic way.
 */
public class AiException extends RuntimeException {

    private final int statusCode;

    public AiException(String message) {
        super(message);
        this.statusCode = -1;
    }

    public AiException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = -1;
    }

    public AiException(String message, int statusCode) {
        super(message);
        this.statusCode = statusCode;
    }

    public AiException(String message, int statusCode, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    /**
     * HTTP status code from the provider, or {@code -1} if not applicable.
     */
    public int statusCode() {
        return statusCode;
    }
}
