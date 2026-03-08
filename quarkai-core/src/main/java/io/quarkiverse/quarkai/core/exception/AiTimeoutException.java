package io.quarkiverse.quarkai.core.exception;

/**
 * Thrown when a request to the AI provider times out.
 */
public class AiTimeoutException extends AiException {

    public AiTimeoutException(String message) {
        super(message, 408);
    }

    public AiTimeoutException(String message, Throwable cause) {
        super(message, 408, cause);
    }
}
