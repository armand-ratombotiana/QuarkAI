package io.quarkiverse.quarkai.core.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class TokenUsageTest {

    @Test
    void total_isComputedFromPromptAndCompletion() {
        TokenUsage usage = TokenUsage.of(100, 50);
        assertThat(usage.promptTokens()).isEqualTo(100);
        assertThat(usage.completionTokens()).isEqualTo(50);
        assertThat(usage.totalTokens()).isEqualTo(150);
    }

    @Test
    void total_explicitOverride() {
        TokenUsage usage = TokenUsage.of(100, 50, 200);
        assertThat(usage.totalTokens()).isEqualTo(200);
    }

    @Test
    void zero_sentinel() {
        TokenUsage zero = TokenUsage.zero();
        assertThat(zero.promptTokens()).isZero();
        assertThat(zero.completionTokens()).isZero();
        assertThat(zero.totalTokens()).isZero();
    }

    @Test
    void negativeTokens_throw() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> TokenUsage.of(-1, 10))
                .withMessageContaining("non-negative");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> TokenUsage.of(10, -5))
                .withMessageContaining("non-negative");
    }

    @Test
    void equalsAndHashCode() {
        TokenUsage a = TokenUsage.of(10, 20);
        TokenUsage b = TokenUsage.of(10, 20);
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void toString_containsValues() {
        TokenUsage usage = TokenUsage.of(10, 20);
        assertThat(usage.toString())
                .contains("10").contains("20").contains("30");
    }
}
