package io.quarkiverse.quarkai.quarkus.deployment;

import io.quarkiverse.quarkai.quarkus.runtime.QuarkAiProducers;
import io.quarkiverse.quarkai.quarkus.runtime.QuarkAiRecorder;
import io.quarkiverse.quarkai.quarkus.runtime.config.QuarkAiConfig;
import io.quarkus.arc.deployment.AdditionalBeanBuildItem;
import io.quarkus.arc.deployment.BeanContainerBuildItem;
import io.quarkus.deployment.annotations.BuildProducer;
import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.annotations.ExecutionTime;
import io.quarkus.deployment.annotations.Record;
import io.quarkus.deployment.builditem.FeatureBuildItem;

/**
 * Quarkus deployment-time processor for the QuarkAI extension.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Registers the {@code quarkai} feature flag
 *   <li>Forces {@link QuarkAiProducers} into the CDI container so that
 *       {@code @Inject ChatModel} resolution never fails at build time
 *   <li>Validates that a supported provider is configured
 * </ul>
 */
public class QuarkAiProcessor {

    private static final String FEATURE = "quarkai";

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    @BuildStep
    AdditionalBeanBuildItem registerProducers() {
        return AdditionalBeanBuildItem.unremovableOf(QuarkAiProducers.class);
    }

    @BuildStep
    @Record(ExecutionTime.RUNTIME_INIT)
    void validateConfig(QuarkAiConfig config,
                        BeanContainerBuildItem beanContainer,
                        QuarkAiRecorder recorder) {
        recorder.validateProvider(config.provider());
    }
}
