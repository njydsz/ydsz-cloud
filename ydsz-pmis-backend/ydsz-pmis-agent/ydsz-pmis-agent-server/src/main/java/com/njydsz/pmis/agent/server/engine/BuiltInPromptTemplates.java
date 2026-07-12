package com.njydsz.pmis.agent.server.engine.prompt;

import java.util.Map;

/**
 * 内置默认 Prompt 模板（P2-2 落地）。
 *
 * <p>当数据库中无对应模板时，使用这些内置默认值作为降级，确保系统在
 * 无 DB 环境下（如单元测试）也能正常工作。
 *
 * <p>这些模板与 {@code ReActLoop} 和 {@code FlowGeneratorAgent} 中
 * 原先硬编码的 prompt 文本完全一致，确保行为向后兼容。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P2-2)
 */
public final class BuiltInPromptTemplates {

    private BuiltInPromptTemplates() {}

    // ==================== 模板文本（必须声明在 TEMPLATES Map 之前） ====================

    /** ReAct 输出格式说明（从 ReActLoop.REACT_FORMAT_INSTRUCTION 迁移） */
    public static final String REACT_FORMAT_INSTRUCTION_TEXT = """
            你正在参与 ReAct 推理循环（Thought → Action → Observation）。
            每一步你必须输出以下 JSON 结构（不要使用 markdown 代码块包裹）：
            {
              "thought": "对当前步骤的思考（为何选择此 Action）",
              "action": "工具名 或 final_answer",
              "parameters": { "参数名": "参数值" },
              "finalAnswer": null
            }

            规则：
            1. 若需要调用工具获取信息，action 填写工具名，parameters 填写工具参数，finalAnswer 必须为 null。
            2. 若已得到最终答案，action 必须填写 "final_answer"，parameters 必须为 null，finalAnswer 填写最终答案。
            3. 你可以最多思考 5 步，请合理规划工具调用顺序。
            4. 工具执行结果会以 "[步骤 N 观察]" 的形式追加在用户问题之后。""";

    /** FlowGeneratorAgent 系统提示词（从 FlowGeneratorAgent.buildSystemPrompt() 迁移） */
    public static final String FLOW_GENERATOR_SYSTEM_TEXT = """
            你是一名资深的工作流（BPMN 2.0）建模专家。请根据用户提供的自然语言流程描述，
            生成一段符合 BPMN 2.0 规范的 XML 流程定义。

            要求：
            1. 根元素必须为 <bpmn:definitions>，并声明 bpmn / bpmndi / dc / di 命名空间；
               targetNamespace 使用 "http://njydsz.com/pmis/flow"。
            2. 流程必须包含：开始节点（startEvent）、至少一个审批节点（userTask）、结束节点（endEvent）。
            3. 当描述中存在条件分支（如"3天以上需经理审批"）时，使用 exclusiveGateway（排他网关）
               配合 sequenceFlow 的 conditionExpression 表达分支。
            4. 节点之间使用 <bpmn:sequenceFlow> 连接，sourceRef / targetRef 引用节点 id。
            5. 为每个节点设置语义化 id 与中文 name。

            工作流程建议：
            - 先生成 BPMN XML，调用 bpmn_validate 工具校验结构完整性
            - 校验通过后，在 final_answer 中输出完整的 BPMN XML（纯 XML 文本，不要 JSON 包裹）
            - 在 final_answer 步骤的 thought 中用一句话描述流程特点""";

    /** FlowGeneratorAgent 用户提示词模板（从 FlowGeneratorAgent.buildUserPrompt() 迁移） */
    public static final String FLOW_GENERATOR_USER_TEXT = """
            请根据以下描述生成 BPMN 2.0 流程定义 XML：

            ${description}""";

    // ==================== 模板映射 ====================

    /** 内置模板映射：templateCode → content */
    private static final Map<String, String> TEMPLATES = Map.of(
            PromptTemplateCodes.REACT_FORMAT_INSTRUCTION, REACT_FORMAT_INSTRUCTION_TEXT,
            PromptTemplateCodes.FLOW_GENERATOR_SYSTEM, FLOW_GENERATOR_SYSTEM_TEXT,
            PromptTemplateCodes.FLOW_GENERATOR_USER, FLOW_GENERATOR_USER_TEXT
    );

    /**
     * 获取内置模板内容。
     *
     * @param code 模板编码
     * @return 模板内容；不存在或 code 为 null/空 返回 null
     */
    public static String get(String code) {
        if (code == null || code.isEmpty()) {
            return null;
        }
        return TEMPLATES.get(code);
    }

    /**
     * 判断是否存在内置模板。
     *
     * @param code 模板编码
     * @return true=存在；code 为 null/空 返回 false
     */
    public static boolean contains(String code) {
        if (code == null || code.isEmpty()) {
            return false;
        }
        return TEMPLATES.containsKey(code);
    }
}
