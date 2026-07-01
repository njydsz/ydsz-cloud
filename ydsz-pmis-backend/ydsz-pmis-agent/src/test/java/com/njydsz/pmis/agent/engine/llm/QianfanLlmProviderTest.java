package com.njydsz.pmis.agent.engine.llm;

import com.njydsz.pmis.agent.engine.AgentContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * QianfanLlmProvider 单元测试 (批次 22 P1-5)
 *
 * <p>覆盖: name / API Key 缺失降级 / 不可达 fallback / 不可达 throw
 */
@DisplayName("QianfanLlmProvider 百度千帆")
class QianfanLlmProviderTest {

    private AgentContext ctx;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        ctx = new AgentContext();
        ctx.setBizType("TEST");
        ctx.setTraceId("trace-qf-001");
    }

    @Test
    @DisplayName("name = qianfan")
    void name() {
        QianfanLlmProvider p = new QianfanLlmProvider("bce-test", "ernie-3.5-8k",
                "https://qianfan.baidubce.com", 1000L, 0, true);
        assertThat(p.name()).isEqualTo("qianfan");
    }

    @Test
    @DisplayName("API Key 为空时降级到 mock")
    void emptyApiKeyFallback() {
        QianfanLlmProvider p = new QianfanLlmProvider("", "ernie-3.5-8k",
                "https://qianfan.baidubce.com", 1000L, 0, true);
        String r = p.chat("s", "u", ctx);
        assertThat(r).contains("NORMAL");
    }

    @Test
    @DisplayName("不可达 fallback")
    void unreachableFallback() {
        QianfanLlmProvider p = new QianfanLlmProvider("bce-fake", "ernie-3.5-8k",
                "http://127.0.0.1:1", 200L, 0, true);
        String r = p.chat("s", "u", ctx);
        assertThat(r).contains("NORMAL");
    }

    @Test
    @DisplayName("不可达 throw")
    void unreachableThrows() {
        QianfanLlmProvider p = new QianfanLlmProvider("bce-fake", "ernie-3.5-8k",
                "http://127.0.0.1:1", 200L, 0, false);
        assertThatThrownBy(() -> p.chat("s", "u", ctx))
                .isInstanceOf(RuntimeException.class);
    }
}
