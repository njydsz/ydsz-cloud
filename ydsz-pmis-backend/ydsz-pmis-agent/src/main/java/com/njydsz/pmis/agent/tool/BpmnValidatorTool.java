package com.njydsz.pmis.agent.tool;

import com.njydsz.pmis.agent.engine.AgentContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * BPMN XML 结构完整性校验工具（P1-1 落地）
 *
 * <p>内置 Agent 工具，用于在流程定义入库 / 发布前对 BPMN 2.0 XML 进行轻量级
 * 结构校验，确保流程图至少包含 definitions / process / startEvent / endEvent
 * 等关键节点，避免无效流程进入运行时引擎。
 *
 * <p>校验维度（基于字符串包含匹配，非严格 XML 解析）：
 * <ul>
 *   <li>{@code <bpmn:definitions>} 开始标签存在</li>
 *   <li>{@code </bpmn:definitions>} 结束标签存在</li>
 *   <li>{@code <bpmn:process} 流程定义存在</li>
 *   <li>{@code <bpmn:startEvent} 起始事件存在</li>
 *   <li>{@code <bpmn:endEvent} 结束事件存在</li>
 * </ul>
 *
 * <p>调用示例（LLM function-calling）：
 * <pre>
 * {
 *   "name": "bpmn_validate",
 *   "parameters": { "bpmnXml": "<bpmn:definitions>...</bpmn:definitions>" }
 * }
 * </pre>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P1-1)
 */
@Slf4j
@Component
public class BpmnValidatorTool implements AgentTool {

    /** BPMN 2.0 命名空间前缀 */
    private static final String BPMN_NS_PREFIX = "bpmn:";

    /** 必须存在的结构标签片段 */
    private static final String TAG_DEFINITIONS_OPEN = "<" + BPMN_NS_PREFIX + "definitions>";
    private static final String TAG_DEFINITIONS_CLOSE = "</" + BPMN_NS_PREFIX + "definitions>";
    private static final String TAG_PROCESS = "<" + BPMN_NS_PREFIX + "process";
    private static final String TAG_START_EVENT = "<" + BPMN_NS_PREFIX + "startEvent";
    private static final String TAG_END_EVENT = "<" + BPMN_NS_PREFIX + "endEvent";

    @Override
    public String name() {
        return "bpmn_validate";
    }

    @Override
    public String description() {
        return "校验 BPMN 2.0 XML 结构完整性（definitions/process/startEvent/endEvent）";
    }

    @Override
    public Map<String, Class<?>> parameterSchema() {
        return Map.of("bpmnXml", String.class);
    }

    @Override
    public ToolResult execute(Map<String, Object> parameters, AgentContext ctx) {
        // 参数提取与空值校验
        Object raw = parameters == null ? null : parameters.get("bpmnXml");
        String bpmnXml = raw == null ? null : String.valueOf(raw);
        if (bpmnXml == null || bpmnXml.isBlank()) {
            log.warn("[bpmn_validate] BPMN XML 为空, bizRef={}", ctx == null ? null : ctx.getBizRef());
            return ToolResult.failure("BPMN XML 为空");
        }

        log.info("[bpmn_validate] 开始校验 BPMN XML, length={}, bizRef={}",
                bpmnXml.length(), ctx == null ? null : ctx.getBizRef());

        // 逐项校验关键结构元素，收集缺失项
        List<String> missingElements = new ArrayList<>();
        if (!bpmnXml.contains(TAG_DEFINITIONS_OPEN)) {
            missingElements.add(TAG_DEFINITIONS_OPEN);
        }
        if (!bpmnXml.contains(TAG_DEFINITIONS_CLOSE)) {
            missingElements.add(TAG_DEFINITIONS_CLOSE);
        }
        if (!bpmnXml.contains(TAG_PROCESS)) {
            missingElements.add(TAG_PROCESS);
        }
        if (!bpmnXml.contains(TAG_START_EVENT)) {
            missingElements.add(TAG_START_EVENT);
        }
        if (!bpmnXml.contains(TAG_END_EVENT)) {
            missingElements.add(TAG_END_EVENT);
        }

        boolean valid = missingElements.isEmpty();

        // 构造文本输出（LLM 可读的观察结果）
        String output;
        if (valid) {
            output = "BPMN XML 结构校验通过：definitions / process / startEvent / endEvent 均存在。";
        } else {
            output = "BPMN XML 结构校验未通过，缺失元素：" + missingElements;
        }

        // 构造结构化数据（供程序逻辑使用）
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("valid", valid);
        data.put("missingElements", missingElements);

        log.info("[bpmn_validate] 校验完成, valid={}, missing={}", valid, missingElements);

        return ToolResult.success(output, data);
    }
}
