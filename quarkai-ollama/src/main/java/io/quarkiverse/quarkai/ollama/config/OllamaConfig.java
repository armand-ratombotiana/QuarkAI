package io.quarkiverse.quarkai.ollama.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

/**
 * Config mapping for the Ollama local model provider.
 *
 * <p>Example:
 * <pre>
 * quarkai.ollama.base-url=http://localhost:11434
 * quarkai.ollama.model=llama3.2
 * </pre>
 */
@ConfigMapping(prefix = "quarkai.ollama")
public interface OllamaConfig {

    @WithName("base-url")
    @WithDefault("http://localhost:11434")
    String baseUrl();

    @WithName("model")
    @WithDefault("llama3.2")
    String model();

    @WithName("timeout-seconds")
    @WithDefault("120")
    long timeoutSeconds();
}
