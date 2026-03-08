package io.quarkiverse.quarkai.anthropic.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

/**
 * MicroProfile Config mapping for the Anthropic Claude provider.
 *
 * <p>Example {@code application.properties}:
 * <pre>
 * quarkai.anthropic.api-key=sk-ant-...
 * quarkai.anthropic.model=claude-3-5-sonnet-20241022
 * </pre>
 */
@ConfigMapping(prefix = "quarkai.anthropic")
public interface AnthropicConfig {

    @WithName("api-key")
    String apiKey();

    @WithName("model")
    @WithDefault("claude-3-5-sonnet-20241022")
    String model();

    @WithName("base-url")
    @WithDefault("https://api.anthropic.com/v1")
    String baseUrl();

    @WithName("timeout-seconds")
    @WithDefault("30")
    long timeoutSeconds();

    @WithName("api-version")
    @WithDefault("2023-06-01")
    String apiVersion();

    @WithName("max-tokens")
    @WithDefault("1024")
    int maxTokens();
}
