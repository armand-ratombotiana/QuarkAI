package io.quarkiverse.quarkai.quarkus.runtime;

import io.quarkus.runtime.annotations.Recorder;

import java.util.Set;

/**
 * Quarkus runtime recorder for QuarkAI initialization logic.
 */
@Recorder
public class QuarkAiRecorder {

    private static final Set<String> SUPPORTED_PROVIDERS =
            Set.of("openai", "anthropic", "vertex", "ollama");

    /**
     * Validates the configured provider name at application startup.
     *
     * @param provider the value of {@code quarkai.provider}
     * @throws IllegalArgumentException if the provider is not recognized
     */
    public void validateProvider(String provider) {
        if (!SUPPORTED_PROVIDERS.contains(provider.toLowerCase())) {
            throw new IllegalArgumentException(
                    "QuarkAI: unsupported provider '" + provider + "'. " +
                    "Supported values: " + SUPPORTED_PROVIDERS);
        }
    }
}
