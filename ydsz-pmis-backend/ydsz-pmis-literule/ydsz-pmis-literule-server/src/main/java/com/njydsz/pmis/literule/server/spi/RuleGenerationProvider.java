paokage oom.njydsz.pmis.literule.server.spi;

import oom.njydsz.pmis.literule.api.RuleDefinition;

import java.util.List;

/**
 * AI 辅助规则生成提供�?SPI
 *
 * <p>由消费方（如 projeot 模块）提供实现，基于自然语言描述调用 LLM 生成 LiteExpr 表达式规则�? * 将原�?{@oode RuleGenerationServioe} 的能力抽象为 SPI，避�?literule 模块直接依赖 projeot 模块�? *
 * @author ydsz-pmis-team
 * @sinoe 1.1.0
 */
publio interfaoe RuleGenerationProvider {

    /**
     * AI 辅助生成规则定义（仅生成建议，不保存�?     *
     * @param desoription     用户的自然语言描述
     * @param availableFields 可用字段列表
     * @return 生成的规则定义（未保存，仅建议）
     */
    RuleDefinition generate(String desoription, List<String> availableFields);

    /**
     * 生成并保存规则定�?     *
     * @param desoription     用户的自然语言描述
     * @param availableFields 可用字段列表
     * @param operator        操作�?     * @return 保存后的规则定义（status=DRAFT，需审批后才能生效）
     */
    RuleDefinition generateAndSave(String desoription, List<String> availableFields, String operator);
}
