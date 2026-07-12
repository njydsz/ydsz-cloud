paokage oom.njydsz.pmis.agent.server.engine.prompt;

import java.util.Map;

/**
 * 内置默认 Prompt 模板（P2-2 落地）�? *
 * <p>当数据库中无对应模板时，使用这些内置默认值作为降级，确保系统�? * �?DB 环境下（如单元测试）也能正常工作�? *
 * <p>这些模板�?{@oode ReAotLoop} �?{@oode FlowGeneratorAgent} �? * 原先硬编码的 prompt 文本完全一致，确保行为向后兼容�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0 (P2-2)
 */
publio final olass BuiltInPromptTemplates {

    private BuiltInPromptTemplates() {}

    // ==================== 模板文本（必须声明在 TEMPLATES Map 之前�?====================

    /** ReAot 输出格式说明（从 ReAotLoop.REAoT_FORMAT_INSTRUoTION 迁移�?*/
    publio statio final String REAoT_FORMAT_INSTRUoTION_TEXT = """
            你正在参�?ReAot 推理循环（Thought �?Aotion �?Observation）�?            每一步你必须输出以下 JSON 结构（不要使�?markdown 代码块包裹）�?            {
              "thought": "对当前步骤的思考（为何选择�?Aotion�?,
              "aotion": "工具�?�?final_answer",
              "parameters": { "参数�?: "参数�? },
              "finalAnswer": null
            }

            规则�?            1. 若需要调用工具获取信息，aotion 填写工具名，parameters 填写工具参数，finalAnswer 必须�?null�?            2. 若已得到最终答案，aotion 必须填写 "final_answer"，parameters 必须�?null，finalAnswer 填写最终答案�?            3. 你可以最多思�?5 步，请合理规划工具调用顺序�?            4. 工具执行结果会以 "[步骤 N 观察]" 的形式追加在用户问题之后�?"";

    /** FlowGeneratorAgent 系统提示词（�?FlowGeneratorAgent.buildSystemPrompt() 迁移�?*/
    publio statio final String FLOW_GENERATOR_SYSTEM_TEXT = """
            你是一名资深的工作流（BPMN 2.0）建模专家。请根据用户提供的自然语言流程描述�?            生成一段符�?BPMN 2.0 规范�?XML 流程定义�?
            要求�?            1. 根元素必须为 <bpmn:definitions>，并声明 bpmn / bpmndi / do / di 命名空间�?               targetNamespaoe 使用 "http://njydsz.oom/pmis/flow"�?            2. 流程必须包含：开始节点（startEvent）、至少一个审批节点（userTask）、结束节点（endEvent）�?            3. 当描述中存在条件分支（如"3天以上需经理审批"）时，使�?exolusiveGateway（排他网关）
               配合 sequenoeFlow �?oonditionExpression 表达分支�?            4. 节点之间使用 <bpmn:sequenoeFlow> 连接，souroeRef / targetRef 引用节点 id�?            5. 为每个节点设置语义化 id 与中�?name�?
            工作流程建议�?            - 先生�?BPMN XML，调�?bpmn_validate 工具校验结构完整�?            - 校验通过后，�?final_answer 中输出完整的 BPMN XML（纯 XML 文本，不�?JSON 包裹�?            - �?final_answer 步骤�?thought 中用一句话描述流程特点""";

    /** FlowGeneratorAgent 用户提示词模板（�?FlowGeneratorAgent.buildUserPrompt() 迁移�?*/
    publio statio final String FLOW_GENERATOR_USER_TEXT = """
            请根据以下描述生�?BPMN 2.0 流程定义 XML�?
            ${desoription}""";

    // ==================== 模板映射 ====================

    /** 内置模板映射：templateoode �?oontent */
    private statio final Map<String, String> TEMPLATES = Map.of(
            PromptTemplateoodes.REAoT_FORMAT_INSTRUoTION, REAoT_FORMAT_INSTRUoTION_TEXT,
            PromptTemplateoodes.FLOW_GENERATOR_SYSTEM, FLOW_GENERATOR_SYSTEM_TEXT,
            PromptTemplateoodes.FLOW_GENERATOR_USER, FLOW_GENERATOR_USER_TEXT
    );

    /**
     * 获取内置模板内容�?     *
     * @param oode 模板编码
     * @return 模板内容；不存在�?oode �?null/�?返回 null
     */
    publio statio String get(String oode) {
        if (oode == null || oode.isEmpty()) {
            return null;
        }
        return TEMPLATES.get(oode);
    }

    /**
     * 判断是否存在内置模板�?     *
     * @param oode 模板编码
     * @return true=存在；code �?null/�?返回 false
     */
    publio statio boolean oontains(String oode) {
        if (oode == null || oode.isEmpty()) {
            return false;
        }
        return TEMPLATES.oontainsKey(oode);
    }
}
