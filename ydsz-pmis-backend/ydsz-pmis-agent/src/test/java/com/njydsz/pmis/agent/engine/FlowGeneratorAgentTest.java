package com.njydsz.pmis.agent.engine;

import com.njydsz.pmis.agent.engine.react.ReActLoop;
import com.njydsz.pmis.agent.engine.react.ReActResult;
import com.njydsz.pmis.agent.engine.react.ReActStep;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * AI 一句话生成流程 Agent 单元测试（P1-2 重构版）
 *
 * <p>覆盖：
 * <ul>
 *   <li>description 为空时返回 INFO + "未提供流程描述"</li>
 *   <li>ReAct 循环抛异常时返回 RED + "ReAct 循环异常"</li>
 *   <li>ReAct 失败时返回 RED + "ReAct 推理失败"</li>
 *   <li>ReAct 成功但 final_answer 为空时返回 YELLOW + "LLM 返回为空"</li>
 *   <li>ReAct 成功且 BPMN XML 完整时 valid=true / level=RECOMMEND</li>
 *   <li>ReAct 成功但 BPMN XML 不完整时 valid=false / level=YELLOW</li>
 *   <li>summary 取自 ReAct 终止步骤的 thought</li>
 *   <li>payload 包含 reactSteps 字段</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("FlowGeneratorAgent AI 流程生成 Agent 测试（P1-2 ReAct 重构版）")
class FlowGeneratorAgentTest {

    @Mock
    private ReActLoop reactLoop;

    private FlowGeneratorAgent agent;

    @BeforeEach
    void setUp() {
        agent = new FlowGeneratorAgent(reactLoop);
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

    /** 构造完整的 BPMN XML */
    private String buildBpmnXml() {
        return "<bpmn:definitions xmlns:bpmn=\"http://www.omg.org/spec/BPMN/20100524/MODEL\">"
                + "<bpmn:process id=\"process1\">"
                + "<bpmn:startEvent id=\"start\"/>"
                + "<bpmn:userTask id=\"approve\" name=\"审批\"/>"
                + "<bpmn:endEvent id=\"end\"/>"
                + "</bpmn:process>"
                + "</bpmn:definitions>";
    }

    /** 构造 ReAct 成功结果（单步 final_answer） */
    private ReActResult successResult(String finalAnswer, String thought) {
        ReActStep step = new ReActStep();
        step.setStepIndex(1);
        step.setThought(thought);
        step.setAction(ReActLoop.ACTION_FINAL_ANSWER);
        step.setFinalAnswer(finalAnswer);
        return ReActResult.success(finalAnswer, List.of(step));
    }

    /** 构造 ReAct 成功结果（多步：先工具调用，后 final_answer） */
    private ReActResult successResultMultiStep(String finalAnswer, String thought) {
        List<ReActStep> steps = new ArrayList<>();

        ReActStep step1 = new ReActStep();
        step1.setStepIndex(1);
        step1.setThought("需要校验 BPMN XML");
        step1.setAction("bpmn_validate");
        step1.setParameters(Map.of("bpmnXml", finalAnswer));
        step1.setObservation("BPMN XML 结构校验通过");
        steps.add(step1);

        ReActStep step2 = new ReActStep();
        step2.setStepIndex(2);
        step2.setThought(thought);
        step2.setAction(ReActLoop.ACTION_FINAL_ANSWER);
        step2.setFinalAnswer(finalAnswer);
        steps.add(step2);

        return ReActResult.success(finalAnswer, steps);
    }

    /** mock ReActLoop.run 返回指定结果 */
    private void mockReActResult(ReActResult result) {
        when(reactLoop.run(anyString(), anyString(), any())).thenReturn(result);
    }

    /** mock ReActLoop.run 抛指定异常 */
    private void mockReActException(Exception ex) {
        when(reactLoop.run(anyString(), anyString(), any())).thenThrow(ex);
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
        @DisplayName("description 为空时返回 INFO + '未提供流程描述'，不调用 ReAct")
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

    // ==================== ReAct 异常测试 ====================

    @Nested
    @DisplayName("ReAct 异常测试")
    class ReactExceptionTest {

        @Test
        @DisplayName("ReActLoop.run 抛异常时返回 RED + 'ReAct 循环异常'")
        void shouldReturnRedWhenReActThrowsException() {
            mockReActException(new RuntimeException("LLM 服务不可用"));

            AgentResult r = agent.execute(ctx("请假审批流程"));

            assertThat(r.getAlertLevel()).isEqualTo(AgentAlertLevel.RED);
            assertThat(r.getScore()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(r.getConfidence()).isEqualByComparingTo(BigDecimal.valueOf(0.2));
            assertThat(r.getSuggestion()).contains("ReAct 循环异常");
            assertThat(r.getMatchedRules()).contains("REACT_ERROR");
            assertThat(r.getPayload().get("bpmnXml")).isEqualTo("");
        }
    }

    // ==================== ReAct 失败测试 ====================

    @Nested
    @DisplayName("ReAct 失败测试")
    class ReactFailureTest {

        @Test
        @DisplayName("ReAct 失败时返回 RED + 'ReAct 推理失败'")
        void shouldReturnRedWhenReActFails() {
            ReActResult failure = ReActResult.failure("达到最大循环次数: 5", new ArrayList<>());
            mockReActResult(failure);

            AgentResult r = agent.execute(ctx("请假审批流程"));

            assertThat(r.getAlertLevel()).isEqualTo(AgentAlertLevel.RED);
            assertThat(r.getConfidence()).isEqualByComparingTo(BigDecimal.valueOf(0.2));
            assertThat(r.getSuggestion()).contains("ReAct 推理失败");
            assertThat(r.getSuggestion()).contains("达到最大循环次数");
            assertThat(r.getMatchedRules()).contains("REACT_FAILED");
            assertThat(r.getPayload().get("bpmnXml")).isEqualTo("");
        }

        @Test
        @DisplayName("ReAct 成功但 final_answer 为 null 时返回 YELLOW + 'LLM 返回为空'")
        void shouldReturnYellowWhenFinalAnswerNull() {
            ReActResult result = successResult(null, "思考完成");
            mockReActResult(result);

            AgentResult r = agent.execute(ctx("请假审批流程"));

            assertThat(r.getAlertLevel()).isEqualTo(AgentAlertLevel.YELLOW);
            assertThat(r.getConfidence()).isEqualByComparingTo(BigDecimal.valueOf(0.3));
            assertThat(r.getSuggestion()).isEqualTo("LLM 返回为空");
            assertThat(r.getMatchedRules()).contains("EMPTY_LLM_OUTPUT");
        }

        @Test
        @DisplayName("ReAct 成功但 final_answer 为空字符串时返回 YELLOW + 'LLM 返回为空'")
        void shouldReturnYellowWhenFinalAnswerEmpty() {
            ReActResult result = successResult("", "思考完成");
            mockReActResult(result);

            AgentResult r = agent.execute(ctx("请假审批流程"));

            assertThat(r.getAlertLevel()).isEqualTo(AgentAlertLevel.YELLOW);
            assertThat(r.getSuggestion()).isEqualTo("LLM 返回为空");
        }

        @Test
        @DisplayName("ReAct 成功但 final_answer 为空白字符时返回 YELLOW + 'LLM 返回为空'")
        void shouldReturnYellowWhenFinalAnswerBlank() {
            ReActResult result = successResult("   ", "思考完成");
            mockReActResult(result);

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
        @DisplayName("ReAct 成功且 BPMN XML 完整时 valid=true / level=RECOMMEND")
        void shouldReturnValidWhenBpmnXmlComplete() {
            String bpmnXml = buildBpmnXml();
            ReActResult result = successResult(bpmnXml, "请假审批流程");
            mockReActResult(result);

            AgentResult r = agent.execute(ctx("请假审批：直属领导审批 → 部门经理审批"));

            assertThat(r.getAlertLevel()).isEqualTo(AgentAlertLevel.RECOMMEND);
            assertThat(r.getScore()).isEqualByComparingTo(BigDecimal.valueOf(0.8));
            assertThat(r.getConfidence()).isEqualByComparingTo(BigDecimal.valueOf(0.75));
            assertThat(r.getPayload().get("bpmnXml")).isEqualTo(bpmnXml);
            assertThat(r.getPayload().get("valid")).isEqualTo(true);
            assertThat(r.getPayload().get("summary")).isEqualTo("请假审批流程");
            assertThat(r.getPayload().get("reactSteps")).isEqualTo(1);
            assertThat(r.getMatchedRules()).contains("VALID_BPMN");
            assertThat(r.getSuggestion()).contains("已根据描述生成");
        }

        @Test
        @DisplayName("ReAct 成功但 BPMN XML 不完整时 valid=false / level=YELLOW")
        void shouldReturnInvalidWhenBpmnXmlIncomplete() {
            ReActResult result = successResult("这是一个普通文本，没有 BPMN 内容", "无效输出");
            mockReActResult(result);

            AgentResult r = agent.execute(ctx("请假审批流程"));

            assertThat(r.getAlertLevel()).isEqualTo(AgentAlertLevel.YELLOW);
            assertThat(r.getScore()).isEqualByComparingTo(BigDecimal.valueOf(0.4));
            assertThat(r.getConfidence()).isEqualByComparingTo(BigDecimal.valueOf(0.75));
            assertThat(r.getPayload().get("valid")).isEqualTo(false);
            assertThat(r.getMatchedRules()).contains("INVALID_BPMN");
            assertThat(r.getSuggestion()).contains("未包含完整的 bpmn:definitions");
        }

        @Test
        @DisplayName("BPMN XML 只有开始标签没有结束标签时 valid=false")
        void shouldReturnInvalidWhenMissingCloseTag() {
            String incompleteXml = "<bpmn:definitions xmlns:bpmn=\"http://www.omg.org/spec/BPMN/20100524/MODEL\">"
                    + "<bpmn:process id=\"proc1\">"
                    + "<bpmn:startEvent id=\"start\"/>"
                    + "</bpmn:process>";
            ReActResult result = successResult(incompleteXml, "不完整");
            mockReActResult(result);

            AgentResult r = agent.execute(ctx("请假审批流程"));

            assertThat(r.getAlertLevel()).isEqualTo(AgentAlertLevel.YELLOW);
            assertThat(r.getPayload().get("valid")).isEqualTo(false);
        }

        @Test
        @DisplayName("多步 ReAct（先校验后输出）时 reactSteps=2，summary 取自终止步骤")
        void shouldReturnCorrectReactStepsForMultiStep() {
            String bpmnXml = buildBpmnXml();
            ReActResult result = successResultMultiStep(bpmnXml, "流程已校验通过，包含审批节点");
            mockReActResult(result);

            AgentResult r = agent.execute(ctx("请假审批流程"));

            assertThat(r.getAlertLevel()).isEqualTo(AgentAlertLevel.RECOMMEND);
            assertThat(r.getPayload().get("valid")).isEqualTo(true);
            assertThat(r.getPayload().get("reactSteps")).isEqualTo(2);
            assertThat(r.getPayload().get("summary")).isEqualTo("流程已校验通过，包含审批节点");
        }

        @Test
        @DisplayName("终止步骤 thought 为 null 时 payload 不包含 summary 字段")
        void shouldNotIncludeSummaryWhenThoughtNull() {
            String bpmnXml = buildBpmnXml();
            ReActResult result = successResult(bpmnXml, null);
            mockReActResult(result);

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
        @DisplayName("matchedRules 包含 description.length 和 react.steps")
        void shouldIncludeDescriptionLengthAndReactSteps() {
            ReActResult result = successResult(buildBpmnXml(), "摘要");
            mockReActResult(result);

            String desc = "请假审批";
            AgentResult r = agent.execute(ctx(desc));

            assertThat(r.getMatchedRules())
                    .anyMatch(s -> s.startsWith("description.length=")
                            && s.contains(String.valueOf(desc.length())));
            assertThat(r.getMatchedRules())
                    .anyMatch(s -> s.startsWith("react.steps="));
        }
    }
}
