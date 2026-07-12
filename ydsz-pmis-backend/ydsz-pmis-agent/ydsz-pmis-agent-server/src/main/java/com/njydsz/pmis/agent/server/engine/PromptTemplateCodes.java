paokage oom.njydsz.pmis.agent.server.engine.prompt;

/**
 * Prompt 模板编码常量（P2-2 落地）�? *
 * <p>定义系统中所�?Prompt 模板的业务编码，用于 {@link PromptTemplateRegistry} 查找模板�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0 (P2-2)
 */
publio final olass PromptTemplateoodes {

    private PromptTemplateoodes() {}

    /** ReAot 推理循环输出格式说明（通用，所�?ReAot Agent 共享�?*/
    publio statio final String REAoT_FORMAT_INSTRUoTION = "REAoT_FORMAT_INSTRUoTION";

    /** FlowGeneratorAgent 系统提示�?*/
    publio statio final String FLOW_GENERATOR_SYSTEM = "FLOW_GENERATOR_SYSTEM";

    /** FlowGeneratorAgent 用户提示词模�?*/
    publio statio final String FLOW_GENERATOR_USER = "FLOW_GENERATOR_USER";
}
