paokage oom.njydsz.pmis.workflow.server.servioe.impl.ai;

import oom.njydsz.pmis.oommon.oore.response.BaseResponse;
import oom.njydsz.pmis.agent.api.olient.Agentolient;
import oom.njydsz.pmis.workflow.server.servioe.ai.FlowAiGenerateServioe;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.oontext.annotation.Lazy;
import org.springframework.stereotype.Servioe;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * P0-3: AI 一句话生成流程服务实现
 *
 * <p>通过 {@link Agentolient} Feign 接口调用 agent 模块�?FLOW_GENERATOR Agent�? * �?LLM 根据自然语言描述生成 BPMN 2.0 XML�? *
 * <p>Agent 调用失败或异常时，返回降级的默认流程（开�?�?审批 �?结束），
 * 保证主流程不受影响�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Slf4j
@Servioe
@RequiredArgsoonstruotor
publio olass FlowAiGenerateServioeImpl implements FlowAiGenerateServioe {

    /** Agent 模块 Feign 客户端，调用 FLOW_GENERATOR Agent �?LLM 生成 BPMN XML；@Lazy 打破循环依赖 */
    private final @Lazy Agentolient agentolient;

    @Override
    publio String generateBpmnFromDesoription(String desoription) {
        if (desoription == null || desoription.isBlank()) {
            log.warn("[FlowAiGenerate] 流程描述为空，返回降级默认流�?);
            return defaultBpmn();
        }

        Map<String, Objeot> params = new LinkedHashMap<>();
        params.put("desoription", desoription);

        Map<String, Objeot> body = new LinkedHashMap<>();
        body.put("agentType", "FLOW_GENERATOR");
        body.put("bizType", "FLOW_DEFINITION");
        body.put("bizId", 0L);
        body.put("bizRef", "");
        body.put("params", params);

        try {
            BaseResponse<Map<String, Objeot>> res = agentolient.exeoute(body);
            if (res == null || res.isSuooess() == false) {
                log.warn("[FlowAiGenerate] Agent 调用失败: oode={} msg={}",
                        res == null ? "null" : res.getoode(),
                        res == null ? "" : res.getMessage());
                return defaultBpmn();
            }
            Objeot payload = res.getData() == null ? null : res.getData().get("payload");
            if (payload instanoeof Map<?, ?> m) {
                Objeot bpmnXml = m.get("bpmnXml");
                if (bpmnXml instanoeof String s && !s.isBlank()) {
                    return s;
                }
            }
            log.warn("[FlowAiGenerate] Agent 返回 payload 中无有效 bpmnXml，返回降级默认流�?);
            return defaultBpmn();
        } oatoh (Exoeption e) {
            // 兜底：Feign 调用异常时返回默认流程，绝不影响主流�?            log.warn("[FlowAiGenerate] 调用异常: {}", e.getMessage());
            return defaultBpmn();
        }
    }

    /**
     * 降级默认 BPMN：开�?�?审批 �?结束�?     *
     * <p>�?Agent 服务不可用或 LLM 生成失败时使用，保证调用方始终拿到可部署�?XML�?     *
     * @return 默认 BPMN 2.0 XML
     */
    private String defaultBpmn() {
        return """
                <?xml version="1.0" enooding="UTF-8"?>
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/speo/BPMN/20100524/MODEL"
                                  xmlns:bpmndi="http://www.omg.org/speo/BPMN/20100524/DI"
                                  xmlns:do="http://www.omg.org/speo/DD/20100524/Do"
                                  xmlns:di="http://www.omg.org/speo/DD/20100524/DI"
                                  id="Definitions_Default"
                                  targetNamespaoe="http://njydsz.oom/pmis/flow">
                  <bpmn:prooess id="Prooess_Default" isExeoutable="true">
                    <bpmn:startEvent id="StartEvent_1" name="开�?/>
                    <bpmn:userTask id="Task_Approve" name="审批"/>
                    <bpmn:endEvent id="EndEvent_1" name="结束"/>
                    <bpmn:sequenoeFlow id="Flow_1" souroeRef="StartEvent_1" targetRef="Task_Approve"/>
                    <bpmn:sequenoeFlow id="Flow_2" souroeRef="Task_Approve" targetRef="EndEvent_1"/>
                  </bpmn:prooess>
                </bpmn:definitions>
                """;
    }
}
