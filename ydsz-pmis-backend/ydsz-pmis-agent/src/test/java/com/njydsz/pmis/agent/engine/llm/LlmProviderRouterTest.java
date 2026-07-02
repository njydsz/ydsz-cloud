package com.njydsz.pmis.agent.engine.llm;

import com.njydsz.pmis.agent.engine.AgentContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * LlmProviderRouter 单元测试 (批次 22 P1-5)
 */
@DisplayName("LlmProviderRouter 路由器")
class LlmProviderRouterTest {

    @Test
    @DisplayName("无 Provider Bean 时降级到 MockLlmProvider")
    void noProviderFallback() {
        ApplicationContext ctx = mock(ApplicationContext.class);
        when(ctx.getBeansOfType(LlmProvider.class)).thenReturn(Map.of());
        LlmProviderRouter router = new LlmProviderRouter(ctx, new MockLlmProvider());
        LlmProvider active = router.active();
        assertThat(active).isInstanceOf(MockLlmProvider.class);
    }

    @Test
    @DisplayName("有 SpringAiLlmProvider 时优先选用")
    void springAiFirst() {
        ApplicationContext ctx = mock(ApplicationContext.class);
        MockLlmProvider mock = new MockLlmProvider();
        SpringAiLlmProvider spring = new SpringAiLlmProvider(null, 1000L, 0, true);
        when(ctx.getBeansOfType(LlmProvider.class)).thenReturn(Map.of("mock", mock, "spring", spring));
        LlmProviderRouter router = new LlmProviderRouter(ctx, mock);
        assertThat(router.active().name()).isEqualTo("spring-ai-openai");
    }

    @Test
    @DisplayName("reload 切换到指定 Provider")
    void reload() {
        ApplicationContext ctx = mock(ApplicationContext.class);
        MockLlmProvider mock = new MockLlmProvider();
        when(ctx.getBeansOfType(LlmProvider.class)).thenReturn(Map.of("mock", mock));
        LlmProviderRouter router = new LlmProviderRouter(ctx, mock);
        router.reload("mock");
        assertThat(router.active()).isInstanceOf(MockLlmProvider.class);
    }

    @Test
    @DisplayName("reload 找不到目标时降级到 mock")
    void reloadMissingFallback() {
        ApplicationContext ctx = mock(ApplicationContext.class);
        MockLlmProvider mock = new MockLlmProvider();
        when(ctx.getBeansOfType(LlmProvider.class)).thenReturn(Map.of("mock", mock));
        LlmProviderRouter router = new LlmProviderRouter(ctx, mock);
        router.reload("non-existent");
        assertThat(router.active()).isInstanceOf(MockLlmProvider.class);
    }

    @Test
    @DisplayName("MockLlmProvider 严重关键词触发 RED")
    void mockKeyWordRed() {
        MockLlmProvider p = new MockLlmProvider();
        String r = p.chat("s", "严重", new AgentContext());
        assertThat(r).contains("RED");
    }

    @Test
    @DisplayName("MockLlmProvider 异常关键词触发 YELLOW")
    void mockKeyWordYellow() {
        MockLlmProvider p = new MockLlmProvider();
        String r = p.chat("s", "异常", new AgentContext());
        assertThat(r).contains("YELLOW");
    }

    @Test
    @DisplayName("MockLlmProvider 默认 NORMAL")
    void mockDefault() {
        MockLlmProvider p = new MockLlmProvider();
        String r = p.chat("s", "普通问题", new AgentContext());
        assertThat(r).contains("NORMAL");
    }

    @Test
    @DisplayName("LlmProvider.parse 默认实现返回 RECOMMEND + 0.5")
    void parseDefault() {
        LlmProvider p = new MockLlmProvider();
        var r = p.parse("anything", new AgentContext());
        assertThat(r.getScore()).isEqualByComparingTo("0.5");
        assertThat(r.getAlertLevel().name()).isEqualTo("RECOMMEND");
    }

    // ========== P1-13 新增 ==========

    @Test
    @DisplayName("getActiveProviderName 返回当前 Provider 名称")
    void getActiveProviderName() {
        ApplicationContext ctx = mock(ApplicationContext.class);
        MockLlmProvider mock = new MockLlmProvider();
        when(ctx.getBeansOfType(LlmProvider.class)).thenReturn(Map.of("mock", mock));
        LlmProviderRouter router = new LlmProviderRouter(ctx, mock);
        assertThat(router.getActiveProviderName()).isEqualTo("mock");
    }

    @Test
    @DisplayName("reload 后 getActiveProviderName 反映新 Provider")
    void getActiveProviderNameAfterReload() {
        ApplicationContext ctx = mock(ApplicationContext.class);
        MockLlmProvider mock = new MockLlmProvider();
        SpringAiLlmProvider spring = new SpringAiLlmProvider(null, 1000L, 0, true);
        when(ctx.getBeansOfType(LlmProvider.class)).thenReturn(Map.of("mock", mock, "spring", spring));
        LlmProviderRouter router = new LlmProviderRouter(ctx, mock);
        // 初始为 spring-ai-openai（优先选择）
        assertThat(router.getActiveProviderName()).isEqualTo("spring-ai-openai");
        // reload 到 mock
        router.reload("mock");
        assertThat(router.getActiveProviderName()).isEqualTo("mock");
    }
}
