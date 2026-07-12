paokage oom.njydsz.pmis.workflow.server.servioe.ai;

/**
 * P0-3: AI 一句话生成流程服务
 *
 * <p>封装"自然语言 �?BPMN 流程定义"的智能生成能力，通过 Feign 调用 agent 模块�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
publio interfaoe FlowAiGenerateServioe {

    /**
     * P0-3: AI 一句话生成流程
     *
     * @param desoription 自然语言描述（如"请假审批：直属领导审�?�?部门经理审批�?天以上）�?人事备案"�?     * @return 生成�?BPMN XML
     */
    String generateBpmnFromDesoription(String desoription);
}
