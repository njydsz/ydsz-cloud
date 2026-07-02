package com.njydsz.pmis.literule.spi;

import com.njydsz.pmis.literule.api.RuleDefinition;

import java.util.List;

/**
 * 规则配置提供者接口（SPI）
 *
 * <p>由消费方（如 execution 模块）提供实现，从数据库/配置中心加载规则定义。
 * literule 模块本身不依赖任何持久层实现，通过此接口反转依赖。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
public interface RuleConfigProvider {

    /**
     * 加载全部启用的规则定义
     *
     * @return 启用的规则定义列表
     */
    List<RuleDefinition> loadEnabledRules();

    /**
     * 加载全部规则定义（含禁用）
     *
     * @return 全部规则定义列表
     */
    List<RuleDefinition> loadAllRules();

    /**
     * 保存规则定义（新增或更新）
     *
     * @param definition 规则定义
     * @param operator   操作人
     * @return 保存后的规则定义（含版本号）
     */
    RuleDefinition save(RuleDefinition definition, String operator);

    /**
     * 切换规则启停状态
     *
     * @param ruleCode 规则编码
     * @param enabled  是否启用
     * @param operator 操作人
     */
    void toggleEnabled(String ruleCode, boolean enabled, String operator);

    /**
     * 根据编码查询单条规则定义
     *
     * @param ruleCode 规则编码
     * @return 规则定义；不存在返回 null
     */
    RuleDefinition findByCode(String ruleCode);
}
