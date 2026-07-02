package com.njydsz.pmis.agent.engine.llm;

import com.njydsz.pmis.agent.engine.AgentContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DashScopeLlmProvider 单元测试 (批次 22 P1-5)
 *
 * <p>覆盖: API Key 缺失降级 / API Key 已配置但 endpoint 不可达 (会重试 N 次后降级到 mock)
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("DashScopeLlmProvider 通义千问")
class DashScopeLlmProviderTest {

    private AgentContext ctx;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        ctx = new AgentContext();
        ctx.setBizType("TEST");
        ctx.setTraceId("trace-001");
    }

    @Test
    @DisplayName("name = dashscope")
    void name() {
        DashScopeLlmProvider p = new DashScopeLlmProvider("sk-test", "qwen-turbo",
                "https://dashscope.aliyuncs.com/compatible-mode", 1000L, 0, true);
        assertThat(p.name()).isEqualTo("dashscope");
    }

    @Test
    @DisplayName("API Key 为空时降级到 mock")
    void emptyApiKeyFallback() {
        DashScopeLlmProvider p = new DashScopeLlmProvider("", "qwen-turbo",
                "https://dashscope.aliyuncs.com/compatible-mode", 1000L, 0, true);
        String r = p.chat("s", "u", ctx);
        assertThat(r).contains("NORMAL");
    }

    @Test
    @DisplayName("API Key = null 时降级到 mock")
    void nullApiKeyFallback() {
        DashScopeLlmProvider p = new DashScopeLlmProvider(null, "qwen-turbo",
                "https://dashscope.aliyuncs.com/compatible-mode", 1000L, 0, true);
        String r = p.chat("s", "u", ctx);
        assertThat(r).contains("NORMAL");
    }

    @Test
    @DisplayName("API 不可达时 (无 fallback) 抛 RuntimeException")
    void unreachableThrows() {
        // 使用无效 URL: 127.0.0.1:1 必然 connection refused
        DashScopeLlmProvider p = new DashScopeLlmProvider("sk-fake", "qwen-turbo",
                "http://127.0.0.1:1", 200L, 0, false);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> p.chat("s", "u", ctx))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("API 不可达时 (有 fallback) 降级到 mock")
    void unreachableFallback() {
        DashScopeLlmProvider p = new DashScopeLlmProvider("sk-fake", "qwen-turbo",
                "http://127.0.0.1:1", 200L, 0, true);
        String r = p.chat("s", "u", ctx);
        assertThat(r).contains("NORMAL");
    }
}
