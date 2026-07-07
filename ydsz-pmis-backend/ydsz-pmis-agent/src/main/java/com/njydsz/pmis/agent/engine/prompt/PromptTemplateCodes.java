package com.njydsz.pmis.agent.engine.prompt;

/**
 * Prompt 模板编码常量（P2-2 落地）。
 *
 * <p>定义系统中所有 Prompt 模板的业务编码，用于 {@link PromptTemplateRegistry} 查找模板。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P2-2)
 */
public final class PromptTemplateCodes {

    private PromptTemplateCodes() {}

    /** ReAct 推理循环输出格式说明（通用，所有 ReAct Agent 共享） */
    public static final String REACT_FORMAT_INSTRUCTION = "REACT_FORMAT_INSTRUCTION";

    /** FlowGeneratorAgent 系统提示词 */
    public static final String FLOW_GENERATOR_SYSTEM = "FLOW_GENERATOR_SYSTEM";

    /** FlowGeneratorAgent 用户提示词模板 */
    public static final String FLOW_GENERATOR_USER = "FLOW_GENERATOR_USER";
}
