package io.quarkiverse.quarkai.core.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.quarkiverse.quarkai.core.model.TokenUsage;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Central Micrometer metrics collector for AI model calls.
 *
 * <p>Meters created:
 * <ul>
 *   <li>{@code quarkai.requests.total} — tagged by {@code provider}, {@code model}, {@code outcome}
 *   <li>{@code quarkai.tokens.prompt} — distribution summary of prompt tokens
 *   <li>{@code quarkai.tokens.completion} — distribution summary of completion tokens
 *   <li>{@code quarkai.latency} — timer for end-to-end request duration
 * </ul>
 */
@ApplicationScoped
public class AiMetrics {

    private final MeterRegistry registry;

    @Inject
    public AiMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    /**
     * Records a successful AI call.
     *
     * @param provider e.g. {@code "openai"}, {@code "anthropic"}
     * @param model    model identifier
     * @param usage    token usage
     */
    public void recordSuccess(String provider, String model, TokenUsage usage) {
        requestCounter(provider, model, "success").increment();
        if (usage != null) {
            promptTokensSummary(provider, model).record(usage.promptTokens());
            completionTokensSummary(provider, model).record(usage.completionTokens());
        }
    }

    /**
     * Records a failed AI call.
     *
     * @param provider    e.g. {@code "openai"}
     * @param model       model identifier
     * @param errorType   e.g. {@code "auth"}, {@code "rate_limit"}, {@code "timeout"}, {@code "unknown"}
     */
    public void recordFailure(String provider, String model, String errorType) {
        requestCounter(provider, model, "error").increment();
        registry.counter("quarkai.errors.total",
                "provider", provider,
                "model", model,
                "type", errorType).increment();
    }

    /**
     * Returns a {@link Timer} for measuring end-to-end latency.
     *
     * <p>Usage: {@code try (Timer.Sample s = metrics.startTimer()) { ... s.stop(metrics.latencyTimer(provider, model)); }}
     */
    public Timer.Sample startTimer() {
        return Timer.start(registry);
    }

    public Timer latencyTimer(String provider, String model) {
        return Timer.builder("quarkai.latency")
                .description("End-to-end AI request latency")
                .tag("provider", provider)
                .tag("model", model)
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(registry);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Counter requestCounter(String provider, String model, String outcome) {
        return registry.counter("quarkai.requests.total",
                "provider", provider,
                "model", model,
                "outcome", outcome);
    }

    private DistributionSummary promptTokensSummary(String provider, String model) {
        return DistributionSummary.builder("quarkai.tokens.prompt")
                .description("Prompt token count per request")
                .tag("provider", provider)
                .tag("model", model)
                .register(registry);
    }

    private DistributionSummary completionTokensSummary(String provider, String model) {
        return DistributionSummary.builder("quarkai.tokens.completion")
                .description("Completion token count per request")
                .tag("provider", provider)
                .tag("model", model)
                .register(registry);
    }
}
