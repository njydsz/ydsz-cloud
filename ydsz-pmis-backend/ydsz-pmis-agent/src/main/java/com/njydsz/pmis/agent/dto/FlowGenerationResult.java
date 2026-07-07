package com.njydsz.pmis.agent.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * AI 流程生成结构化输出 DTO（P1-5 落地）
 *
 * <p>用于 {@code FlowGeneratorAgent} 通过 {@code LlmProvider.chatForJson()} 获取
 * LLM 生成的 BPMN 2.0 流程定义，替代 P0-3 阶段基于正则提取 XML 的脆弱方式。
 *
 * <p>LLM 输出 JSON 格式示例：
 * <pre>
 * {
 *   "bpmnXml": "&lt;bpmn:definitions&gt;...&lt;/bpmn:definitions&gt;",
 *   "summary": "请假审批：直属领导 → 部门经理 → 人事备案"
 * }
 * </pre>
 *
 * <p>Agent 侧根据 {@link #bpmnXml} 是否包含完整的
 * {@code <bpmn:definitions>} 和 {@code </bpmn:definitions>} 判断 {@code valid} 字段。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P1-5)
 */
@Data
public class FlowGenerationResult implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** BPMN 2.0 XML 文本（根元素 {@code <bpmn:definitions>}） */
    private String bpmnXml;

    /** 流程摘要说明（LLM 生成的一句话流程描述） */
    private String summary;
}
