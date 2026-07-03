package com.njydsz.pmis.workflow.service.impl;

import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.common.feign.AgentClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FlowAiGenerateServiceImpl 单元测试
 *
 * <p>P0-3：覆盖 AI 一句话生成流程 Service 的核心场景，验证 Agent 调用成功、
 * 失败降级、异常兜底以及默认 BPMN 内容的正确性。
 *
 * <p>覆盖场景：
 * <ul>
 *   <li>description 为空：直接返回 defaultBpmn()，不调用 AgentClient</li>
 *   <li>AgentClient 返回成功：res.code==0 且 bpmnXml 非空 → 返回该 XML</li>
 *   <li>AgentClient 返回失败：res.code!=0 → 降级返回 defaultBpmn()</li>
 *   <li>AgentClient 返回 payload 为空：降级返回 defaultBpmn()</li>
 *   <li>AgentClient 返回 bpmnXml 为空：降级返回 defaultBpmn()</li>
 *   <li>AgentClient 抛异常：降级返回 defaultBpmn()，不抛出</li>
 *   <li>defaultBpmn 内容校验：包含 startEvent、userTask、endEvent 三个节点</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.5.0
 */
@ExtendWith(MockitoExtension.class)
class FlowAiGenerateServiceImplTest {

    @Mock
    private AgentClient agentClient;

    private FlowAiGenerateServiceImpl service;

    @BeforeEach
    void setUp() {
        // @Lazy 仅影响 Spring 注入时机，测试中直接构造被测对象即可
        service = new FlowAiGenerateServiceImpl(agentClient);
    }

    @Test
    @DisplayName("description 为空时直接返回降级默认流程，不调用 AgentClient")
    void shouldReturnDefaultBpmnWhenDescriptionIsBlank() {
        String result = service.generateBpmnFromDescription("   ");

        assertThat(result).isNotBlank();
        assertThat(result).contains("startEvent", "userTask", "endEvent");
        verify(agentClient, never()).execute(any());
    }

    @Test
    @DisplayName("AgentClient 返回成功且 bpmnXml 非空时返回该 XML")
    void shouldReturnBpmnXmlWhenAgentSuccess() {
        String expectedXml = "<bpmn:definitions><bpmn:process id=\"P1\"/></bpmn:definitions>";
        Map<String, Object> payload = new HashMap<>();
        payload.put("bpmnXml", expectedXml);
        Map<String, Object> data = new HashMap<>();
        data.put("payload", payload);
        when(agentClient.execute(any())).thenReturn(Result.ok(data));

        String result = service.generateBpmnFromDescription("三天请假流程");

        assertThat(result).isEqualTo(expectedXml);
        verify(agentClient).execute(any());
    }

    @Test
    @DisplayName("AgentClient 返回失败 code!=0 时降级返回默认流程")
    void shouldReturnDefaultBpmnWhenAgentFailed() {
        when(agentClient.execute(any())).thenReturn(Result.failed(500, "internal error"));

        String result = service.generateBpmnFromDescription("审批流程");

        assertThat(result).contains("startEvent", "userTask", "endEvent");
        verify(agentClient).execute(any());
    }

    @Test
    @DisplayName("AgentClient 返回 payload 为空时降级返回默认流程")
    void shouldReturnDefaultBpmnWhenPayloadNull() {
        // data 为 null，res.getData().get("payload") 路径不会命中
        when(agentClient.execute(any())).thenReturn(Result.ok(null));

        String result = service.generateBpmnFromDescription("审批流程");

        assertThat(result).contains("startEvent", "userTask", "endEvent");
        verify(agentClient).execute(any());
    }

    @Test
    @DisplayName("AgentClient 返回 bpmnXml 为空时降级返回默认流程")
    void shouldReturnDefaultBpmnWhenBpmnXmlBlank() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("bpmnXml", "   "); // 空白字符串
        Map<String, Object> data = new HashMap<>();
        data.put("payload", payload);
        when(agentClient.execute(any())).thenReturn(Result.ok(data));

        String result = service.generateBpmnFromDescription("审批流程");

        assertThat(result).contains("startEvent", "userTask", "endEvent");
        verify(agentClient).execute(any());
    }

    @Test
    @DisplayName("AgentClient 抛异常时降级返回默认流程，不向外抛出")
    void shouldReturnDefaultBpmnWhenAgentThrows() {
        when(agentClient.execute(any())).thenThrow(new RuntimeException("feign timeout"));

        String result = service.generateBpmnFromDescription("审批流程");

        assertThat(result).contains("startEvent", "userTask", "endEvent");
        verify(agentClient).execute(any());
    }

    @Test
    @DisplayName("defaultBpmn 默认流程应包含 startEvent、userTask、endEvent 三个节点")
    void defaultBpmnShouldContainCoreNodes() {
        // description=null 同样走降级路径，可用于触发 defaultBpmn()
        String result = service.generateBpmnFromDescription(null);

        assertThat(result)
                .contains("startEvent")
                .contains("userTask")
                .contains("endEvent")
                .contains("<bpmn:definitions")
                .contains("<bpmn:process");
        verify(agentClient, never()).execute(any());
    }
}
