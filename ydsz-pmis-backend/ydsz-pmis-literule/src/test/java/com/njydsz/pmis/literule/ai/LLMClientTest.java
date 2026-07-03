package com.njydsz.pmis.literule.ai;

import com.njydsz.pmis.literule.api.RuleDefinition;
import com.njydsz.pmis.literule.expr.AviatorExpressionEvaluator;
import com.njydsz.pmis.literule.expr.ExpressionValidationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * LLM 客户端 + 规则 LLM 服务测试
 */
@DisplayName("LLM 客户端与规则 LLM 服务测试")
class LLMClientTest {

    private MockLLMClient mockClient;
    private RuleLLMService ruleLLMService;

    @BeforeEach
    void setUp() {
        mockClient = new MockLLMClient();
        ExpressionValidationService validator = new ExpressionValidationService(
                new AviatorExpressionEvaluator(true));
        ruleLLMService = new RuleLLMService(mockClient, validator);
    }

    @Test
    @DisplayName("Mock 客户端应返回确定性的 NL2Rule 响应")
    void shouldReturnMockNL2Rule() {
        String out = mockClient.chat("system", "natural language to rule", null);
        assertEquals("evmRedCount >= 3", out);
    }

    @Test
    @DisplayName("Mock 客户端应返回确定性 describe 响应")
    void shouldReturnMockDescribe() {
        String out = mockClient.chat("system", "describe this rule", null);
        assertTrue(out.contains("预警"));
    }

    @Test
    @DisplayName("Mock 客户端应返回确定性 optimize 响应")
    void shouldReturnMockOptimize() {
        String out = mockClient.chat("system", "optimize this expression", null);
        assertTrue(out.contains("变量"));
    }

    @Test
    @DisplayName("chatWithHistory 应取最后一条消息作为输入")
    void shouldUseLastMessageFromHistory() {
        Map<String, String> m1 = new HashMap<>();
        m1.put("role", "user");
        m1.put("content", "natural language to rule");
        String out = mockClient.chatWithHistory(java.util.Collections.singletonList(m1), null);
        assertEquals("evmRedCount >= 3", out);
    }

    @Test
    @DisplayName("RuleLLMService 应能解析 LLM 输出为 RuleDefinition")
    void shouldParseLLMOutput() {
        String raw = "```json\n" +
                "{\"code\":\"ai-test\",\"name\":\"测试\",\"conditionExpression\":\"x > 1\"," +
                "\"defaultSeverity\":\"RED\",\"description\":\"测试规则\"}\n```";
        // 替换 mock 行为，构造临时 mock 客户端
        LLMClient stub = new LLMClient() {
            @Override
            public String chat(String s, String u, Map<String, Object> o) { return raw; }
            @Override
            public String chatWithHistory(java.util.List<Map<String, String>> messages,
                                          Map<String, Object> options) { return raw; }
            @Override public String provider() { return "STUB"; }
            @Override public String model() { return "stub"; }
        };
        ExpressionValidationService validator = new ExpressionValidationService(
                new AviatorExpressionEvaluator(true));
        RuleLLMService svc = new RuleLLMService(stub, validator);
        RuleDefinition rule = svc.naturalLanguageToRule("当 x 大于 1 时告警");
        assertEquals("ai-test", rule.getCode());
        assertEquals("测试", rule.getName());
        assertEquals("x > 1", rule.getConditionExpression());
    }

    @Test
    @DisplayName("空自然语言输入应抛 IllegalArgumentException")
    void shouldThrowOnEmptyInput() {
        try {
            ruleLLMService.naturalLanguageToRule("");
            assertTrue(false, "应抛异常");
        } catch (IllegalArgumentException expected) {
            // pass
        }
    }

    @Test
    @DisplayName("LLM 不可用时 naturalLanguageToRule 应降级返回空壳定义")
    void shouldFallbackWhenLLMUnavailable() {
        LLMClient failingClient = new LLMClient() {
            @Override
            public String chat(String s, String u, Map<String, Object> o) {
                throw new LLMException("STUB", "API Key 未配置");
            }
            @Override
            public String chatWithHistory(java.util.List<Map<String, String>> messages,
                                          Map<String, Object> options) {
                throw new LLMException("STUB", "API Key 未配置");
            }
            @Override public String provider() { return "STUB"; }
            @Override public String model() { return "stub"; }
        };
        ExpressionValidationService validator = new ExpressionValidationService(
                new AviatorExpressionEvaluator(true));
        RuleLLMService svc = new RuleLLMService(failingClient, validator);
        RuleDefinition rule = svc.naturalLanguageToRule("测试输入");
        assertNotNull(rule);
        assertEquals("测试输入", rule.getName());
        assertTrue(rule.getDescription().contains("LLM"));
    }

    @Test
    @DisplayName("describeRule 与 optimizeExpression 在 LLM 失败时应返回 null 而非抛异常")
    void shouldReturnNullOnLLMFailure() {
        LLMClient failingClient = new LLMClient() {
            @Override
            public String chat(String s, String u, Map<String, Object> o) {
                throw new LLMException("STUB", "网络错误");
            }
            @Override
            public String chatWithHistory(java.util.List<Map<String, String>> messages,
                                          Map<String, Object> options) {
                throw new LLMException("STUB", "网络错误");
            }
            @Override public String provider() { return "STUB"; }
            @Override public String model() { return "stub"; }
        };
        ExpressionValidationService validator = new ExpressionValidationService(
                new AviatorExpressionEvaluator(true));
        RuleLLMService svc = new RuleLLMService(failingClient, validator);
        RuleDefinition rule = RuleDefinition.builder().code("R").name("R").build();
        assertEquals(null, svc.describeRule(rule));
        assertEquals(null, svc.optimizeExpression("x > 1"));
    }

    @Test
    @DisplayName("LLMException 应保留 provider 与 statusCode")
    void shouldPreserveExceptionInfo() {
        LLMException e1 = new LLMException("P1", "msg");
        assertEquals("P1", e1.getProvider());
        assertEquals(0, e1.getStatusCode());
        LLMException e2 = new LLMException("P2", 401, "auth fail");
        assertEquals(401, e2.getStatusCode());
        assertEquals("P2", e2.getProvider());
    }
}
