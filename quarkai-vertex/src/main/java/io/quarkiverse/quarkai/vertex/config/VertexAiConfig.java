package io.quarkiverse.quarkai.vertex.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

/**
 * Config mapping for Google Vertex AI (Gemini) provider.
 *
 * <p>Example:
 * <pre>
 * quarkai.vertex.project-id=my-gcp-project
 * quarkai.vertex.location=us-central1
 * quarkai.vertex.model=gemini-1.5-pro
 * quarkai.vertex.access-token=ya29...
 * </pre>
 */
@ConfigMapping(prefix = "quarkai.vertex")
public interface VertexAiConfig {

    @WithName("project-id")
    String projectId();

    @WithName("location")
    @WithDefault("us-central1")
    String location();

    @WithName("model")
    @WithDefault("gemini-1.5-pro")
    String model();

    /** OAuth2 access token — rotate regularly or use Workload Identity. */
    @WithName("access-token")
    String accessToken();

    @WithName("timeout-seconds")
    @WithDefault("60")
    long timeoutSeconds();
}
