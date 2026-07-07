package com.njydsz.pmis.agent.engine;

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
import static org.mockito.Mockito.when;

/**
 * AI 一句话生成流程 Agent 单元测试
 *
 * <p>覆盖：
 * <ul>
 *   <li>description 为空时返回 INFO + "未提供流程描述"</li>
 *   <li>LLM 调用抛异常时返回 RED + "LLM 调用失败"</li>
 *   <li>LLM 返回空字符串时返回 YELLOW + "LLM 返回为空"</li>
 *   <li>LLM 返回 ```xml ... ``` 代码块时被正确提取</li>
 *   <li>LLM 返回裸 XML（含 <?xml 或 <bpmn:definitions）时被正确截取</li>
 *   <li>LLM 返回不含 bpmn:definitions 时 valid=false / level=YELLOW</li>
 *   <li>LLM 返回完整 bpmn:definitions 时 valid=true / level=RECOMMEND</li>
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

    /** 设置 LLM 返回值 */
    private void mockLlmResponse(String response) {
        when(llmProviderRouter.active()).thenReturn(llmProvider);
        when(llmProvider.chat(anyString(), anyString(), any())).thenReturn(response);
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
        @DisplayName("LLM 调用抛异常时返回 RED + 'LLM 调用失败'")
        void shouldReturnRedWhenLlmThrowsException() {
            when(llmProviderRouter.active()).thenReturn(llmProvider);
            when(llmProvider.chat(anyString(), anyString(), any()))
                    .thenThrow(new RuntimeException("LLM 服务不可用"));
            when(llmProviderRouter.getActiveProviderName()).thenReturn("mock");

            AgentResult r = agent.execute(ctx("请假审批流程"));

            assertThat(r.getAlertLevel()).isEqualTo(AgentAlertLevel.RED);
            assertThat(r.getScore()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(r.getConfidence()).isEqualByComparingTo(BigDecimal.valueOf(0.2));
            assertThat(r.getSuggestion()).contains("LLM 调用失败");
            assertThat(r.getMatchedRules()).contains("LLM_ERROR");
            assertThat(r.getPayload().get("bpmnXml")).isEqualTo("");
        }

        @Test
        @DisplayName("LLM 返回 null 时返回 YELLOW + 'LLM 返回为空'")
        void shouldReturnYellowWhenLlmReturnsNull() {
            mockLlmResponse(null);

            AgentResult r = agent.execute(ctx("请假审批流程"));

            assertThat(r.getAlertLevel()).isEqualTo(AgentAlertLevel.YELLOW);
            assertThat(r.getConfidence()).isEqualByComparingTo(BigDecimal.valueOf(0.3));
            assertThat(r.getSuggestion()).isEqualTo("LLM 返回为空");
            assertThat(r.getMatchedRules()).contains("EMPTY_LLM_OUTPUT");
        }

        @Test
        @DisplayName("LLM 返回空字符串时返回 YELLOW + 'LLM 返回为空'")
        void shouldReturnYellowWhenLlmReturnsEmpty() {
            mockLlmResponse("");

            AgentResult r = agent.execute(ctx("请假审批流程"));

            assertThat(r.getAlertLevel()).isEqualTo(AgentAlertLevel.YELLOW);
            assertThat(r.getSuggestion()).isEqualTo("LLM 返回为空");
        }

        @Test
        @DisplayName("LLM 返回纯空白时返回 YELLOW + 'LLM 返回为空'")
        void shouldReturnYellowWhenLlmReturnsBlank() {
            mockLlmResponse("   ");

            AgentResult r = agent.execute(ctx("请假审批流程"));

            assertThat(r.getAlertLevel()).isEqualTo(AgentAlertLevel.YELLOW);
            assertThat(r.getSuggestion()).isEqualTo("LLM 返回为空");
        }
    }

    // ==================== XML 提取测试 ====================

    @Nested
    @DisplayName("XML 提取测试")
    class XmlExtractionTest {

        @Test
        @DisplayName("LLM 返回 ```xml ... ``` 代码块时被正确提取")
        void shouldExtractXmlCodeBlock() {
            String bpmnXml = "<bpmn:definitions xmlns:bpmn=\"http://www.omg.org/spec/BPMN/20100524/MODEL\">"
                    + "<bpmn:process id=\"process1\">"
                    + "<bpmn:startEvent id=\"start\"/>"
                    + "<bpmn:userTask id=\"approve\" name=\"审批\"/>"
                    + "<bpmn:endEvent id=\"end\"/>"
                    + "</bpmn:process>"
                    + "</bpmn:definitions>";
            String llmOutput = "这是生成的流程：\n```xml\n" + bpmnXml + "\n```";
            mockLlmResponse(llmOutput);

            AgentResult r = agent.execute(ctx("请假审批流程"));

            assertThat(r.getAlertLevel()).isEqualTo(AgentAlertLevel.RECOMMEND);
            assertThat(r.getScore()).isEqualByComparingTo(BigDecimal.valueOf(0.8));
            assertThat(r.getConfidence()).isEqualByComparingTo(BigDecimal.valueOf(0.75));
            assertThat(r.getPayload().get("bpmnXml")).isEqualTo(bpmnXml);
            assertThat(r.getPayload().get("valid")).isEqualTo(true);
            assertThat(r.getMatchedRules()).contains("VALID_BPMN");
            assertThat(r.getSuggestion()).contains("已根据描述生成");
        }

        @Test
        @DisplayName("LLM 返回裸 XML（以 <?xml 开头）时被正确截取")
        void shouldExtractBareXmlWithDeclaration() {
            String bpmnXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                    + "<bpmn:definitions xmlns:bpmn=\"http://www.omg.org/spec/BPMN/20100524/MODEL\">"
                    + "<bpmn:process id=\"process1\">"
                    + "<bpmn:startEvent id=\"start\"/>"
                    + "<bpmn:endEvent id=\"end\"/>"
                    + "</bpmn:process>"
                    + "</bpmn:definitions>";
            String llmOutput = "好的，这是流程：\n" + bpmnXml + "\n如需修改请告知。";
            mockLlmResponse(llmOutput);

            AgentResult r = agent.execute(ctx("请假审批流程"));

            assertThat(r.getAlertLevel()).isEqualTo(AgentAlertLevel.RECOMMEND);
            assertThat((String) r.getPayload().get("bpmnXml")).startsWith("<?xml");
            assertThat((String) r.getPayload().get("bpmnXml")).endsWith("</bpmn:definitions>");
            assertThat(r.getPayload().get("valid")).isEqualTo(true);
        }

        @Test
        @DisplayName("LLM 返回裸 XML（以 <bpmn:definitions 开头）时被正确截取")
        void shouldExtractBareXmlStartingWithDefinitions() {
            String bpmnXml = "<bpmn:definitions xmlns:bpmn=\"http://www.omg.org/spec/BPMN/20100524/MODEL\">"
                    + "<bpmn:process id=\"process1\">"
                    + "<bpmn:startEvent id=\"start\"/>"
                    + "<bpmn:endEvent id=\"end\"/>"
                    + "</bpmn:process>"
                    + "</bpmn:definitions>";
            mockLlmResponse(bpmnXml);

            AgentResult r = agent.execute(ctx("请假审批流程"));

            assertThat(r.getAlertLevel()).isEqualTo(AgentAlertLevel.RECOMMEND);
            assertThat((String) r.getPayload().get("bpmnXml")).startsWith("<bpmn:definitions");
            assertThat((String) r.getPayload().get("bpmnXml")).endsWith("</bpmn:definitions>");
            assertThat(r.getPayload().get("valid")).isEqualTo(true);
        }

        @Test
        @DisplayName("LLM 返回不含 bpmn:definitions 时 valid=false / level=YELLOW")
        void shouldReturnInvalidWhenNoBpmnDefinitions() {
            String llmOutput = "这是一个普通文本，没有 BPMN 内容";
            mockLlmResponse(llmOutput);

            AgentResult r = agent.execute(ctx("请假审批流程"));

            assertThat(r.getAlertLevel()).isEqualTo(AgentAlertLevel.YELLOW);
            assertThat(r.getScore()).isEqualByComparingTo(BigDecimal.valueOf(0.4));
            assertThat(r.getConfidence()).isEqualByComparingTo(BigDecimal.valueOf(0.75));
            assertThat(r.getPayload().get("valid")).isEqualTo(false);
            assertThat(r.getMatchedRules()).contains("INVALID_BPMN");
            assertThat(r.getSuggestion()).contains("未包含完整的 bpmn:definitions");
        }

        @Test
        @DisplayName("LLM 返回完整 bpmn:definitions 时 valid=true / level=RECOMMEND")
        void shouldReturnValidWhenCompleteBpmnDefinitions() {
            String bpmnXml = "<bpmn:definitions xmlns:bpmn=\"http://www.omg.org/spec/BPMN/20100524/MODEL\">"
                    + "<bpmn:process id=\"proc1\">"
                    + "<bpmn:startEvent id=\"start\"/>"
                    + "<bpmn:userTask id=\"task1\" name=\"部门经理审批\"/>"
                    + "<bpmn:endEvent id=\"end\"/>"
                    + "</bpmn:process>"
                    + "</bpmn:definitions>";
            mockLlmResponse(bpmnXml);

            AgentResult r = agent.execute(ctx("请假审批：直属领导审批 → 部门经理审批"));

            assertThat(r.getAlertLevel()).isEqualTo(AgentAlertLevel.RECOMMEND);
            assertThat(r.getScore()).isEqualByComparingTo(BigDecimal.valueOf(0.8));
            assertThat(r.getPayload().get("valid")).isEqualTo(true);
            assertThat(r.getPayload().get("bpmnXml")).isEqualTo(bpmnXml);
        }

        @Test
        @DisplayName("LLM 返回只有开始标签没有结束标签时 valid=false")
        void shouldReturnInvalidWhenMissingCloseTag() {
            String llmOutput = "<bpmn:definitions xmlns:bpmn=\"http://www.omg.org/spec/BPMN/20100524/MODEL\">"
                    + "<bpmn:process id=\"proc1\">"
                    + "<bpmn:startEvent id=\"start\"/>"
                    + "</bpmn:process>";
            // 缺少 </bpmn:definitions>
            mockLlmResponse(llmOutput);

            AgentResult r = agent.execute(ctx("请假审批流程"));

            assertThat(r.getAlertLevel()).isEqualTo(AgentAlertLevel.YELLOW);
            assertThat(r.getPayload().get("valid")).isEqualTo(false);
        }
    }

    // ==================== matchedRules 测试 ====================

    @Nested
    @DisplayName("matchedRules 测试")
    class MatchedRulesTest {

        @Test
        @DisplayName("matchedRules 包含 description.length")
        void shouldIncludeDescriptionLength() {
            String bpmnXml = "<bpmn:definitions xmlns:bpmn=\"http://www.omg.org/spec/BPMN/20100524/MODEL\">"
                    + "<bpmn:process id=\"proc1\">"
                    + "<bpmn:startEvent id=\"start\"/>"
                    + "<bpmn:endEvent id=\"end\"/>"
                    + "</bpmn:process>"
                    + "</bpmn:definitions>";
            mockLlmResponse(bpmnXml);

            String desc = "请假审批";
            AgentResult r = agent.execute(ctx(desc));

            assertThat(r.getMatchedRules())
                    .anyMatch(s -> s.startsWith("description.length=") && s.contains(String.valueOf(desc.length())));
        }
    }
}
