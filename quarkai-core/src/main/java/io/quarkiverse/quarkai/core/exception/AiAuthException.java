package io.quarkiverse.quarkai.core.exception;

/**
 * Thrown when authentication with the AI provider fails (e.g. invalid API key).
 * Maps to HTTP 401 responses.
 */
public class AiAuthException extends AiException {

    public AiAuthException(String message) {
        super(message, 401);
    }

    public AiAuthException(String message, Throwable cause) {
        super(message, 401, cause);
    }
}
