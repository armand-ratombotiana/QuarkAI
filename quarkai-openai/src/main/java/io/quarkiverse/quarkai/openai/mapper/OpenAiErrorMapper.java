package io.quarkiverse.quarkai.openai.mapper;

import io.quarkiverse.quarkai.core.exception.AiAuthException;
import io.quarkiverse.quarkai.core.exception.AiException;
import io.quarkiverse.quarkai.core.exception.AiRateLimitException;
import io.quarkiverse.quarkai.core.exception.AiTimeoutException;

/**
 * Maps HTTP error status codes and provider error bodies to QuarkAI exceptions.
 */
public final class OpenAiErrorMapper {

    private OpenAiErrorMapper() {}

    /**
     * Converts an HTTP status code and optional error body to a typed {@link AiException}.
     *
     * @param statusCode HTTP status code
     * @param body       raw response body (may be null or empty)
     * @return a typed {@link AiException} subclass
     */
    public static AiException map(int statusCode, String body) {
        String safeBody = body != null ? body : "(empty)";
        return switch (statusCode) {
            case 401 -> new AiAuthException(
                    "OpenAI authentication failed. Check your API key. Body: " + safeBody);
            case 403 -> new AiAuthException(
                    "OpenAI access forbidden. Check permissions. Body: " + safeBody);
            case 408 -> new AiTimeoutException(
                    "OpenAI request timed out. Body: " + safeBody);
            case 429 -> {
                // Try to extract retry-after from body (basic heuristic)
                long retryAfter = extractRetryAfter(safeBody);
                yield new AiRateLimitException(
                        "OpenAI rate limit exceeded. Body: " + safeBody, retryAfter);
            }
            default  -> new AiException(
                    "OpenAI request failed with HTTP " + statusCode + ". Body: " + safeBody,
                    statusCode);
        };
    }

    private static long extractRetryAfter(String body) {
        // A real implementation would parse the Retry-After header.
        // For now, return -1 (unknown).
        return -1L;
    }
}
