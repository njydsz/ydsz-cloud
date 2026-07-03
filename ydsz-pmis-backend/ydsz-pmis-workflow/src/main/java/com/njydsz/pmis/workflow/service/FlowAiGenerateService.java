package com.njydsz.pmis.workflow.service;

/**
 * P0-3: AI 一句话生成流程服务
 *
 * <p>封装"自然语言 → BPMN 流程定义"的智能生成能力，通过 Feign 调用 agent 模块。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface FlowAiGenerateService {

    /**
     * P0-3: AI 一句话生成流程
     *
     * @param description 自然语言描述（如"请假审批：直属领导审批 → 部门经理审批（3天以上）→ 人事备案"）
     * @return 生成的 BPMN XML
     */
    String generateBpmnFromDescription(String description);
}
