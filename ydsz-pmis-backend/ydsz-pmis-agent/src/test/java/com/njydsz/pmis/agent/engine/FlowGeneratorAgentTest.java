package com.njydsz.pmis.agent.engine;

import com.njydsz.pmis.agent.dto.FlowGenerationResult;
import com.njydsz.pmis.agent.engine.llm.LlmProvider;
import com.njydsz.pmis.agent.engine.llm.LlmProviderRouter;
import com.njydsz.pmis.agent.enums.AgentAlertLevel;
import com.njydsz.pmis.agent.enums.AgentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * AI 一句话生成流程 Agent 单元测试（P1-5 重构版）
 *
 * <p>覆盖：
 * <ul>
 *   <li>description 为空时返回 INFO + "未提供流程描述"</li>
 *   <li>LLM chatForJson 抛异常时返回 RED + "LLM 调用失败"</li>
 *   <li>LLM 返回 null / 空 bpmnXml 时返回 YELLOW + "LLM 返回为空"</li>
 *   <li>LLM 返回完整 bpmn:definitions 时 valid=true / level=RECOMMEND</li>
 *   <li>LLM 返回不含 bpmn:definitions 时 valid=false / level=YELLOW</li>
 *   <li>LLM 返回缺少结束标签时 valid=false</li>
 *   <li>payload 包含 summary 字段</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("FlowGeneratorAgent AI 流程生成 Agent 测试")
class FlowGeneratorAgentTest {

    @Mock
    private LlmProviderRouter llmProviderRouter;

    @Mock
    private LlmProvider llmProvider;

    private FlowGeneratorAgent agent;

    @BeforeEach
    void setUp() {
        agent = new FlowGeneratorAgent(llmProviderRouter);
    }

    // ==================== 辅助方法 ====================

    /** 构造 AgentContext */
    private AgentContext ctx(String description) {
        AgentContext ctx = new AgentContext();
        ctx.setBizType("workflow");
        ctx.setBizId("W001");
        ctx.setBizRef("WF-001");
        ctx.setCallerId("U001");
        ctx.setCallerName("张三");
        ctx.setSource("unit-test");
        Map<String, Object> params = new HashMap<>();
        if (description != null) {
            params.put("description", description);
        }
        ctx.setParams(params);
        return ctx;
    }

    /** 构造 BPMN XML */
    private String buildBpmnXml() {
        return "<bpmn:definitions xmlns:bpmn=\"http://www.omg.org/spec/BPMN/20100524/MODEL\">"
                + "<bpmn:process id=\"process1\">"
                + "<bpmn:startEvent id=\"start\"/>"
                + "<bpmn:userTask id=\"approve\" name=\"审批\"/>"
                + "<bpmn:endEvent id=\"end\"/>"
                + "</bpmn:process>"
                + "</bpmn:definitions>";
    }

    /** mock chatForJson 返回指定结果 */
    private void mockLlmResult(FlowGenerationResult result) {
        when(llmProviderRouter.active()).thenReturn(llmProvider);
        when(llmProvider.chatForJson(anyString(), anyString(),
                eq(FlowGenerationResult.class), any())).thenReturn(result);
        when(llmProviderRouter.getActiveProviderName()).thenReturn("mock");
    }

    /** mock chatForJson 抛指定异常 */
    private void mockLlmException(Exception ex) {
        when(llmProviderRouter.active()).thenReturn(llmProvider);
        when(llmProvider.chatForJson(anyString(), anyString(),
                eq(FlowGenerationResult.class), any())).thenThrow(ex);
        when(llmProviderRouter.getActiveProviderName()).thenReturn("mock");
    }

    // ==================== 基础属性测试 ====================

    @Nested
    @DisplayName("基础属性测试")
    class BasicTest {

        @Test
        @DisplayName("type() 返回 FLOW_GENERATOR")
        void shouldReturnFlowGeneratorType() {
            assertThat(agent.type()).isEqualTo(AgentType.FLOW_GENERATOR);
        }
    }

    // ==================== 空描述测试 ====================

    @Nested
    @DisplayName("空描述测试")
    class EmptyDescriptionTest {

        @Test
        @DisplayName("description 为空时返回 INFO + '未提供流程描述'")
        void shouldReturnInfoWhenDescriptionEmpty() {
            AgentResult r = agent.execute(ctx(""));

            assertThat(r.getAlertLevel()).isEqualTo(AgentAlertLevel.INFO);
            assertThat(r.getScore()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(r.getConfidence()).isEqualByComparingTo(BigDecimal.valueOf(0.3));
            assertThat(r.getSuggestion()).isEqualTo("未提供流程描述");
            assertThat(r.getMatchedRules()).contains("NO_DESCRIPTION");
            assertThat(r.getPayload().get("bpmnXml")).isEqualTo("");
        }

        @Test
        @DisplayName("description=null 时返回 INFO + '未提供流程描述'")
        void shouldReturnInfoWhenDescriptionNull() {
            AgentResult r = agent.execute(ctx(null));

            assertThat(r.getAlertLevel()).isEqualTo(AgentAlertLevel.INFO);
            assertThat(r.getSuggestion()).isEqualTo("未提供流程描述");
        }

        @Test
        @DisplayName("description=空白字符时返回 INFO + '未提供流程描述'")
        void shouldReturnInfoWhenDescriptionBlank() {
            AgentResult r = agent.execute(ctx("   "));

            assertThat(r.getAlertLevel()).isEqualTo(AgentAlertLevel.INFO);
            assertThat(r.getSuggestion()).isEqualTo("未提供流程描述");
        }

        @Test
        @DisplayName("params=null 时返回 INFO + '未提供流程描述'")
        void shouldReturnInfoWhenParamsNull() {
            AgentContext ctx = new AgentContext();
            ctx.setParams(null);
            AgentResult r = agent.execute(ctx);

            assertThat(r.getAlertLevel()).isEqualTo(AgentAlertLevel.INFO);
            assertThat(r.getSuggestion()).isEqualTo("未提供流程描述");
        }
    }

    // ==================== LLM 异常测试 ====================

    @Nested
    @DisplayName("LLM 异常测试")
    class LlmExceptionTest {

        @Test
        @DisplayName("chatForJson 抛异常时返回 RED + 'LLM 调用失败'")
        void shouldReturnRedWhenLlmThrowsException() {
            mockLlmException(new RuntimeException("LLM 服务不可用"));

            AgentResult r = agent.execute(ctx("请假审批流程"));

            assertThat(r.getAlertLevel()).isEqualTo(AgentAlertLevel.RED);
            assertThat(r.getScore()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(r.getConfidence()).isEqualByComparingTo(BigDecimal.valueOf(0.2));
            assertThat(r.getSuggestion()).contains("LLM 调用失败");
            assertThat(r.getMatchedRules()).contains("LLM_ERROR");
            assertThat(r.getPayload().get("bpmnXml")).isEqualTo("");
        }

        @Test
        @DisplayName("chatForJson 抛 JSON 解析异常时返回 RED + 'LLM 调用失败'")
        void shouldReturnRedWhenJsonParseFails() {
            // 模拟 LLM 返回非 JSON 文本导致 chatForJson 抛 RuntimeException
            mockLlmException(new RuntimeException("LLM 输出非合法 JSON: not a json"));

            AgentResult r = agent.execute(ctx("请假审批流程"));

            assertThat(r.getAlertLevel()).isEqualTo(AgentAlertLevel.RED);
            assertThat(r.getSuggestion()).contains("LLM 调用失败");
            assertThat(r.getMatchedRules()).contains("LLM_ERROR");
        }

        @Test
        @DisplayName("chatForJson 返回 null 时返回 YELLOW + 'LLM 返回为空'")
        void shouldReturnYellowWhenLlmReturnsNull() {
            mockLlmResult(null);

            AgentResult r = agent.execute(ctx("请假审批流程"));

            assertThat(r.getAlertLevel()).isEqualTo(AgentAlertLevel.YELLOW);
            assertThat(r.getConfidence()).isEqualByComparingTo(BigDecimal.valueOf(0.3));
            assertThat(r.getSuggestion()).isEqualTo("LLM 返回为空");
            assertThat(r.getMatchedRules()).contains("EMPTY_LLM_OUTPUT");
        }

        @Test
        @DisplayName("chatForJson 返回 bpmnXml 为空字符串时返回 YELLOW + 'LLM 返回为空'")
        void shouldReturnYellowWhenBpmnXmlEmpty() {
            FlowGenerationResult result = new FlowGenerationResult();
            result.setBpmnXml("");
            mockLlmResult(result);

            AgentResult r = agent.execute(ctx("请假审批流程"));

            assertThat(r.getAlertLevel()).isEqualTo(AgentAlertLevel.YELLOW);
            assertThat(r.getSuggestion()).isEqualTo("LLM 返回为空");
        }

        @Test
        @DisplayName("chatForJson 返回 bpmnXml 为空白字符时返回 YELLOW + 'LLM 返回为空'")
        void shouldReturnYellowWhenBpmnXmlBlank() {
            FlowGenerationResult result = new FlowGenerationResult();
            result.setBpmnXml("   ");
            mockLlmResult(result);

            AgentResult r = agent.execute(ctx("请假审批流程"));

            assertThat(r.getAlertLevel()).isEqualTo(AgentAlertLevel.YELLOW);
            assertThat(r.getSuggestion()).isEqualTo("LLM 返回为空");
        }

        @Test
        @DisplayName("chatForJson 返回 bpmnXml 为 null 时返回 YELLOW + 'LLM 返回为空'")
        void shouldReturnYellowWhenBpmnXmlNull() {
            FlowGenerationResult result = new FlowGenerationResult();
            result.setBpmnXml(null);
            mockLlmResult(result);

            AgentResult r = agent.execute(ctx("请假审批流程"));

            assertThat(r.getAlertLevel()).isEqualTo(AgentAlertLevel.YELLOW);
            assertThat(r.getSuggestion()).isEqualTo("LLM 返回为空");
        }
    }

    // ==================== 结构化输出验证测试 ====================

    @Nested
    @DisplayName("结构化输出验证测试")
    class StructuredOutputTest {

        @Test
        @DisplayName("LLM 返回完整 bpmn:definitions 时 valid=true / level=RECOMMEND")
        void shouldReturnValidWhenCompleteBpmnDefinitions() {
            String bpmnXml = buildBpmnXml();
            FlowGenerationResult result = new FlowGenerationResult();
            result.setBpmnXml(bpmnXml);
            result.setSummary("请假审批流程");
            mockLlmResult(result);

            AgentResult r = agent.execute(ctx("请假审批：直属领导审批 → 部门经理审批"));

            assertThat(r.getAlertLevel()).isEqualTo(AgentAlertLevel.RECOMMEND);
            assertThat(r.getScore()).isEqualByComparingTo(BigDecimal.valueOf(0.8));
            assertThat(r.getConfidence()).isEqualByComparingTo(BigDecimal.valueOf(0.75));
            assertThat(r.getPayload().get("bpmnXml")).isEqualTo(bpmnXml);
            assertThat(r.getPayload().get("valid")).isEqualTo(true);
            assertThat(r.getPayload().get("summary")).isEqualTo("请假审批流程");
            assertThat(r.getMatchedRules()).contains("VALID_BPMN");
            assertThat(r.getSuggestion()).contains("已根据描述生成");
        }

        @Test
        @DisplayName("LLM 返回不含 bpmn:definitions 时 valid=false / level=YELLOW")
        void shouldReturnInvalidWhenNoBpmnDefinitions() {
            FlowGenerationResult result = new FlowGenerationResult();
            result.setBpmnXml("这是一个普通文本，没有 BPMN 内容");
            result.setSummary("无效输出");
            mockLlmResult(result);

            AgentResult r = agent.execute(ctx("请假审批流程"));

            assertThat(r.getAlertLevel()).isEqualTo(AgentAlertLevel.YELLOW);
            assertThat(r.getScore()).isEqualByComparingTo(BigDecimal.valueOf(0.4));
            assertThat(r.getConfidence()).isEqualByComparingTo(BigDecimal.valueOf(0.75));
            assertThat(r.getPayload().get("valid")).isEqualTo(false);
            assertThat(r.getMatchedRules()).contains("INVALID_BPMN");
            assertThat(r.getSuggestion()).contains("未包含完整的 bpmn:definitions");
        }

        @Test
        @DisplayName("LLM 返回只有开始标签没有结束标签时 valid=false")
        void shouldReturnInvalidWhenMissingCloseTag() {
            String incompleteXml = "<bpmn:definitions xmlns:bpmn=\"http://www.omg.org/spec/BPMN/20100524/MODEL\">"
                    + "<bpmn:process id=\"proc1\">"
                    + "<bpmn:startEvent id=\"start\"/>"
                    + "</bpmn:process>";
            // 缺少 </bpmn:definitions>
            FlowGenerationResult result = new FlowGenerationResult();
            result.setBpmnXml(incompleteXml);
            mockLlmResult(result);

            AgentResult r = agent.execute(ctx("请假审批流程"));

            assertThat(r.getAlertLevel()).isEqualTo(AgentAlertLevel.YELLOW);
            assertThat(r.getPayload().get("valid")).isEqualTo(false);
        }

        @Test
        @DisplayName("summary 为 null 时 payload 不包含 summary 字段")
        void shouldNotIncludeSummaryWhenNull() {
            String bpmnXml = buildBpmnXml();
            FlowGenerationResult result = new FlowGenerationResult();
            result.setBpmnXml(bpmnXml);
            result.setSummary(null);
            mockLlmResult(result);

            AgentResult r = agent.execute(ctx("请假审批流程"));

            assertThat(r.getPayload().get("valid")).isEqualTo(true);
            assertThat(r.getPayload()).doesNotContainKey("summary");
        }
    }

    // ==================== matchedRules 测试 ====================

    @Nested
    @DisplayName("matchedRules 测试")
    class MatchedRulesTest {

        @Test
        @DisplayName("matchedRules 包含 description.length")
        void shouldIncludeDescriptionLength() {
            FlowGenerationResult result = new FlowGenerationResult();
            result.setBpmnXml(buildBpmnXml());
            mockLlmResult(result);

            String desc = "请假审批";
            AgentResult r = agent.execute(ctx(desc));

            assertThat(r.getMatchedRules())
                    .anyMatch(s -> s.startsWith("description.length=") && s.contains(String.valueOf(desc.length())));
        }
    }
}
