package io.quarkiverse.quarkai.quarkus.runtime.config;

import io.quarkus.runtime.annotations.ConfigRoot;
import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

/**
 * Root configuration for the QuarkAI Quarkus extension.
 *
 * <p>Example:
 * <pre>
 * quarkai.provider=openai
 * quarkai.openai.api-key=sk-...
 * </pre>
 */
@ConfigMapping(prefix = "quarkai")
@ConfigRoot
public interface QuarkAiConfig {

    /**
     * Active AI provider. Accepted values: {@code openai}, {@code anthropic},
     * {@code vertex}, {@code ollama}.
     */
    @WithName("provider")
    @WithDefault("openai")
    String provider();
}
