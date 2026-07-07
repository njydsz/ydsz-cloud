package com.njydsz.pmis.agent.engine.llm;

import com.njydsz.pmis.agent.engine.AgentContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LLM Provider 路由器单元测试
 *
 * <p>使用真实 GenericApplicationContext 注册 stub LlmProvider Bean，不 mock ApplicationContext。
 * 使用真实 MockLlmProvider 实例，不 mock。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("LlmProviderRouter LLM Provider 路由器测试")
class LlmProviderRouterTest {

    // ==================== 测试用 Stub Provider ====================

    /** Stub Provider - 用于模拟 spring-ai 系列 */
    static class StubLlmProvider implements LlmProvider {
        private final String name;
        private final String response;

        StubLlmProvider(String name, String response) {
            this.name = name;
            this.response = response;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public String chat(String systemPrompt, String userPrompt, AgentContext context) {
            return response;
        }
    }

    // ==================== 辅助方法 ====================

    /** 创建一个空的已 refresh 的 GenericApplicationContext */
    private GenericApplicationContext freshContext() {
        GenericApplicationContext ctx = new GenericApplicationContext();
        ctx.refresh();
        return ctx;
    }

    /** 构造路由器并注入真实 MockLlmProvider */
    private LlmProviderRouter routerWith(GenericApplicationContext ctx) {
        return new LlmProviderRouter(ctx, new MockLlmProvider());
    }

    // ==================== active() 测试 ====================

    @Nested
    @DisplayName("active() 选择策略测试")
    class ActiveTest {

        @Test
        @DisplayName("active() 优先返回 name 以 'spring-ai' 开头的 Provider")
        void shouldPreferSpringAiProvider() {
            GenericApplicationContext ctx = freshContext();
            StubLlmProvider springAiProvider = new StubLlmProvider("spring-ai-openai", "spring-ai-response");
            StubLlmProvider otherProvider = new StubLlmProvider("dashscope", "dashscope-response");
            ctx.getBeanFactory().registerSingleton("springAiProvider", springAiProvider);
            ctx.getBeanFactory().registerSingleton("otherProvider", otherProvider);

            LlmProviderRouter router = routerWith(ctx);
            LlmProvider active = router.active();

            assertThat(active).isSameAs(springAiProvider);
        }

        @Test
        @DisplayName("无任何 Provider 时降级到 MockLlmProvider")
        void shouldFallbackToMockWhenNoProvider() {
            GenericApplicationContext ctx = freshContext();
            LlmProviderRouter router = routerWith(ctx);

            LlmProvider active = router.active();

            assertThat(active).isInstanceOf(MockLlmProvider.class);
        }

        @Test
        @DisplayName("有 Provider 但都不是 spring-ai 时降级到 mock")
        void shouldFallbackToMockWhenNoSpringAiProvider() {
            GenericApplicationContext ctx = freshContext();
            StubLlmProvider dashscopeProvider = new StubLlmProvider("dashscope", "dashscope-response");
            StubLlmProvider qianfanProvider = new StubLlmProvider("qianfan", "qianfan-response");
            ctx.getBeanFactory().registerSingleton("dashscope", dashscopeProvider);
            ctx.getBeanFactory().registerSingleton("qianfan", qianfanProvider);

            LlmProviderRouter router = routerWith(ctx);
            LlmProvider active = router.active();

            assertThat(active).isInstanceOf(MockLlmProvider.class);
        }

        @Test
        @DisplayName("active() 缓存 - 第二次调用不重新扫描 Bean")
        void shouldCacheActiveProvider() {
            GenericApplicationContext ctx = freshContext();
            StubLlmProvider provider = new StubLlmProvider("spring-ai-openai", "cached");
            ctx.getBeanFactory().registerSingleton("provider", provider);

            LlmProviderRouter router = routerWith(ctx);
            LlmProvider first = router.active();
            LlmProvider second = router.active();

            assertThat(first).isSameAs(second);
            assertThat(first).isSameAs(provider);
        }

        @Test
        @DisplayName("多个 spring-ai Provider - 取第一个匹配的")
        void shouldReturnFirstMatchingSpringAiProvider() {
            GenericApplicationContext ctx = freshContext();
            StubLlmProvider first = new StubLlmProvider("spring-ai-openai", "openai");
            StubLlmProvider second = new StubLlmProvider("spring-ai-dashscope", "dashscope");
            ctx.getBeanFactory().registerSingleton("first", first);
            ctx.getBeanFactory().registerSingleton("second", second);

            LlmProviderRouter router = routerWith(ctx);
            LlmProvider active = router.active();

            // 应返回其中一个 spring-ai 开头的（具体取决于 Bean 注册顺序）
            assertThat(active.name()).startsWith("spring-ai");
        }
    }

    // ==================== reload() 测试 ====================

    @Nested
    @DisplayName("reload() 切换测试")
    class ReloadTest {

        @Test
        @DisplayName("reload(name) 切换成功")
        void shouldReloadToSpecifiedProvider() {
            GenericApplicationContext ctx = freshContext();
            StubLlmProvider springAiProvider = new StubLlmProvider("spring-ai-openai", "openai");
            StubLlmProvider dashscopeProvider = new StubLlmProvider("dashscope", "dashscope");
            ctx.getBeanFactory().registerSingleton("springAi", springAiProvider);
            ctx.getBeanFactory().registerSingleton("dashscope", dashscopeProvider);

            LlmProviderRouter router = routerWith(ctx);
            // 初始激活 spring-ai
            router.active();
            // 切换到 dashscope
            router.reload("dashscope");

            LlmProvider active = router.active();
            assertThat(active).isSameAs(dashscopeProvider);
        }

        @Test
        @DisplayName("reload(不存在名称) 降级到 mock")
        void shouldFallbackToMockWhenReloadNonExistentName() {
            GenericApplicationContext ctx = freshContext();
            StubLlmProvider springAiProvider = new StubLlmProvider("spring-ai-openai", "openai");
            ctx.getBeanFactory().registerSingleton("springAi", springAiProvider);

            LlmProviderRouter router = routerWith(ctx);
            router.active();
            // 切换到不存在的 Provider
            router.reload("non-existent-provider");

            LlmProvider active = router.active();
            assertThat(active).isInstanceOf(MockLlmProvider.class);
        }

        @Test
        @DisplayName("reload 后 active() 返回切换后的 Provider")
        void shouldReturnSwitchedProviderAfterReload() {
            GenericApplicationContext ctx = freshContext();
            StubLlmProvider springAiProvider = new StubLlmProvider("spring-ai-openai", "openai");
            ctx.getBeanFactory().registerSingleton("springAi", springAiProvider);

            LlmProviderRouter router = routerWith(ctx);
            // 初始 active = spring-ai
            assertThat(router.active()).isSameAs(springAiProvider);

            // reload 到 mock（不在 context 中，降级）
            router.reload("mock");
            // 此时 active 应为 MockLlmProvider 实例
            LlmProvider active = router.active();
            assertThat(active).isInstanceOf(MockLlmProvider.class);
        }
    }

    // ==================== getActiveProviderName() 测试 ====================

    @Nested
    @DisplayName("getActiveProviderName() 测试")
    class GetActiveProviderNameTest {

        @Test
        @DisplayName("getActiveProviderName() 返回当前 Provider 名称")
        void shouldReturnActiveProviderName() {
            GenericApplicationContext ctx = freshContext();
            StubLlmProvider springAiProvider = new StubLlmProvider("spring-ai-openai", "openai");
            ctx.getBeanFactory().registerSingleton("springAi", springAiProvider);

            LlmProviderRouter router = routerWith(ctx);
            String name = router.getActiveProviderName();

            assertThat(name).isEqualTo("spring-ai-openai");
        }

        @Test
        @DisplayName("无 Provider 时 getActiveProviderName() 返回 'mock'")
        void shouldReturnMockNameWhenNoProvider() {
            GenericApplicationContext ctx = freshContext();
            LlmProviderRouter router = routerWith(ctx);

            String name = router.getActiveProviderName();

            assertThat(name).isEqualTo("mock");
        }

        @Test
        @DisplayName("reload 后 getActiveProviderName() 返回新 Provider 名称")
        void shouldReturnNewProviderNameAfterReload() {
            GenericApplicationContext ctx = freshContext();
            StubLlmProvider dashscopeProvider = new StubLlmProvider("dashscope", "dashscope");
            ctx.getBeanFactory().registerSingleton("dashscope", dashscopeProvider);

            LlmProviderRouter router = routerWith(ctx);
            // 初始降级到 mock
            assertThat(router.getActiveProviderName()).isEqualTo("mock");

            // reload 到 dashscope
            router.reload("dashscope");
            assertThat(router.getActiveProviderName()).isEqualTo("dashscope");
        }
    }

    // ==================== chat() 行为测试 ====================

    @Nested
    @DisplayName("端到端行为测试")
    class EndToEndTest {

        @Test
        @DisplayName("active().chat() 返回 stub 响应")
        void shouldReturnStubResponse() {
            GenericApplicationContext ctx = freshContext();
            StubLlmProvider springAiProvider = new StubLlmProvider("spring-ai-openai", "hello from stub");
            ctx.getBeanFactory().registerSingleton("springAi", springAiProvider);

            LlmProviderRouter router = routerWith(ctx);
            String response = router.active().chat("sys", "user", null);

            assertThat(response).isEqualTo("hello from stub");
        }

        @Test
        @DisplayName("降级到 mock 时返回 MockLlmProvider 的标准输出")
        void shouldReturnMockOutputWhenFallback() {
            GenericApplicationContext ctx = freshContext();
            LlmProviderRouter router = routerWith(ctx);

            String response = router.active().chat("", "普通查询", null);

            // MockLlmProvider 对普通查询返回 NORMAL 等级
            assertThat(response).contains("NORMAL");
        }
    }
}
