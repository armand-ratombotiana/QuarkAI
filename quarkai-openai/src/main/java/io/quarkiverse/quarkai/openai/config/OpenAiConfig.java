package io.quarkiverse.quarkai.openai.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

import java.util.Optional;

/**
 * MicroProfile Config mapping for the OpenAI provider.
 *
 * <p>Example {@code application.properties}:
 * <pre>
 * quarkai.openai.api-key=sk-...
 * quarkai.openai.model=gpt-4o
 * quarkai.openai.base-url=https://api.openai.com/v1
 * quarkai.openai.timeout-seconds=30
 * </pre>
 */
@ConfigMapping(prefix = "quarkai.openai")
public interface OpenAiConfig {

    @WithName("api-key")
    String apiKey();

    @WithName("model")
    @WithDefault("gpt-4o")
    String model();

    @WithName("base-url")
    @WithDefault("https://api.openai.com/v1")
    String baseUrl();

    @WithName("timeout-seconds")
    @WithDefault("30")
    long timeoutSeconds();

    @WithName("organization-id")
    Optional<String> organizationId();
}
