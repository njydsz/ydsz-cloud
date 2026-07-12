package com.njydsz.pmis.workflow.server.service.impl.ai;

import com.njydsz.pmis.common.core.response.BaseResponse;
import com.njydsz.pmis.agent.api.client.AgentClient;
import com.njydsz.pmis.workflow.server.service.ai.FlowAiGenerateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * P0-3: AI 一句话生成流程服务实现
 *
 * <p>通过 {@link AgentClient} Feign 接口调用 agent 模块的 FLOW_GENERATOR Agent，
 * 由 LLM 根据自然语言描述生成 BPMN 2.0 XML。
 *
 * <p>Agent 调用失败或异常时，返回降级的默认流程（开始 → 审批 → 结束），
 * 保证主流程不受影响。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowAiGenerateServiceImpl implements FlowAiGenerateService {

    /** Agent 模块 Feign 客户端，调用 FLOW_GENERATOR Agent 由 LLM 生成 BPMN XML；@Lazy 打破循环依赖 */
    private final @Lazy AgentClient agentClient;

    @Override
    public String generateBpmnFromDescription(String description) {
        if (description == null || description.isBlank()) {
            log.warn("[FlowAiGenerate] 流程描述为空，返回降级默认流程");
            return defaultBpmn();
        }

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("description", description);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("agentType", "FLOW_GENERATOR");
        body.put("bizType", "FLOW_DEFINITION");
        body.put("bizId", 0L);
        body.put("bizRef", "");
        body.put("params", params);

        try {
            BaseResponse<Map<String, Object>> res = agentClient.execute(body);
            if (res == null || res.isSuccess() == false) {
                log.warn("[FlowAiGenerate] Agent 调用失败: code={} msg={}",
                        res == null ? "null" : res.getCode(),
                        res == null ? "" : res.getMessage());
                return defaultBpmn();
            }
            Object payload = res.getData() == null ? null : res.getData().get("payload");
            if (payload instanceof Map<?, ?> m) {
                Object bpmnXml = m.get("bpmnXml");
                if (bpmnXml instanceof String s && !s.isBlank()) {
                    return s;
                }
            }
            log.warn("[FlowAiGenerate] Agent 返回 payload 中无有效 bpmnXml，返回降级默认流程");
            return defaultBpmn();
        } catch (Exception e) {
            // 兜底：Feign 调用异常时返回默认流程，绝不影响主流程
            log.warn("[FlowAiGenerate] 调用异常: {}", e.getMessage());
            return defaultBpmn();
        }
    }

    /**
     * 降级默认 BPMN：开始 → 审批 → 结束。
     *
     * <p>当 Agent 服务不可用或 LLM 生成失败时使用，保证调用方始终拿到可部署的 XML。
     *
     * @return 默认 BPMN 2.0 XML
     */
    private String defaultBpmn() {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                                  xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI"
                                  xmlns:dc="http://www.omg.org/spec/DD/20100524/DC"
                                  xmlns:di="http://www.omg.org/spec/DD/20100524/DI"
                                  id="Definitions_Default"
                                  targetNamespace="http://njydsz.com/pmis/flow">
                  <bpmn:process id="Process_Default" isExecutable="true">
                    <bpmn:startEvent id="StartEvent_1" name="开始"/>
                    <bpmn:userTask id="Task_Approve" name="审批"/>
                    <bpmn:endEvent id="EndEvent_1" name="结束"/>
                    <bpmn:sequenceFlow id="Flow_1" sourceRef="StartEvent_1" targetRef="Task_Approve"/>
                    <bpmn:sequenceFlow id="Flow_2" sourceRef="Task_Approve" targetRef="EndEvent_1"/>
                  </bpmn:process>
                </bpmn:definitions>
                """;
    }
}
