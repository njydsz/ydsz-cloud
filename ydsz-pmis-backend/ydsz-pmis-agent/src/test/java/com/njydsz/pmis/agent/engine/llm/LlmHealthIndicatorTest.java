package com.njydsz.pmis.agent.engine.llm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * LLM 健康检查指标单元测试（P1-13 新增）
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("LlmHealthIndicator 健康检查")
class LlmHealthIndicatorTest {

    @Test
    @DisplayName("Provider 正常时返回 UP 状态")
    void healthUp() {
        LlmProviderRouter router = mock(LlmProviderRouter.class);
        when(router.getActiveProviderName()).thenReturn("mock");
        MockLlmProvider mockProvider = new MockLlmProvider();

        LlmHealthIndicator indicator = new LlmHealthIndicator(router, mockProvider);
        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("provider", "mock");
        assertThat(health.getDetails()).containsEntry("fallback-available", true);
    }

    @Test
    @DisplayName("spring-ai-openai Provider 时正确返回 provider 名称")
    void healthWithSpringAi() {
        LlmProviderRouter router = mock(LlmProviderRouter.class);
        when(router.getActiveProviderName()).thenReturn("spring-ai-openai");
        MockLlmProvider mockProvider = new MockLlmProvider();

        LlmHealthIndicator indicator = new LlmHealthIndicator(router, mockProvider);
        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("provider", "spring-ai-openai");
    }

    @Test
    @DisplayName("Router 抛异常时返回 DOWN 状态")
    void healthDownOnException() {
        LlmProviderRouter router = mock(LlmProviderRouter.class);
        when(router.getActiveProviderName()).thenThrow(new RuntimeException("context not ready"));
        MockLlmProvider mockProvider = new MockLlmProvider();

        LlmHealthIndicator indicator = new LlmHealthIndicator(router, mockProvider);
        Health health = indicator.health();

        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsKey("error");
    }
}
