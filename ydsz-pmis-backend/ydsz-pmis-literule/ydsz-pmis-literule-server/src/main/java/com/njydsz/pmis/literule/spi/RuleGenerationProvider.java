package com.njydsz.pmis.literule.server.spi;

import com.njydsz.pmis.literule.api.RuleDefinition;

import java.util.List;

/**
 * AI 辅助规则生成提供者 SPI
 *
 * <p>由消费方（如 project 模块）提供实现，基于自然语言描述调用 LLM 生成 Aviator 表达式规则。
 * 将原有 {@code RuleGenerationService} 的能力抽象为 SPI，避免 literule 模块直接依赖 project 模块。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
public interface RuleGenerationProvider {

    /**
     * AI 辅助生成规则定义（仅生成建议，不保存）
     *
     * @param description     用户的自然语言描述
     * @param availableFields 可用字段列表
     * @return 生成的规则定义（未保存，仅建议）
     */
    RuleDefinition generate(String description, List<String> availableFields);

    /**
     * 生成并保存规则定义
     *
     * @param description     用户的自然语言描述
     * @param availableFields 可用字段列表
     * @param operator        操作人
     * @return 保存后的规则定义（status=DRAFT，需审批后才能生效）
     */
    RuleDefinition generateAndSave(String description, List<String> availableFields, String operator);
}
