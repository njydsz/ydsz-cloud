package com.njydsz.pmis.literule.server.spi;

import java.util.List;

import com.njydsz.pmis.literule.api.DecisionTreeDefinition;

/**
 * 决策树配置提供者接口（SPI）
 *
 * <p>由消费方提供实现，从数据库加载决策树定义。
 *
 * @author ydsz-pmis-team
 * @since 1.4.0
 */
public interface DecisionTreeConfigProvider {

    /**
     * 加载全部启用的决策树
     *
     * @return 启用的决策树列表
     */
    List<DecisionTreeDefinition> loadEnabledTrees();

    /**
     * 加载全部决策树（含禁用）
     *
     * @return 全部决策树列表
     */
    List<DecisionTreeDefinition> loadAllTrees();

    /**
     * 保存决策树
     *
     * @param definition 决策树定义
     * @param operator   操作人
     * @return 保存后的定义
     */
    DecisionTreeDefinition save(DecisionTreeDefinition definition, String operator);

    /**
     * 切换启停
     *
     * @param ruleCode 规则编码
     * @param enabled  是否启用
     * @param operator 操作人
     */
    void toggleEnabled(String ruleCode, boolean enabled, String operator);

    /**
     * 根据编码查询
     *
     * @param ruleCode 规则编码
     * @return 决策树定义；不存在返回 null
     */
    DecisionTreeDefinition findByCode(String ruleCode);

    /**
     * 删除决策树
     *
     * @param ruleCode 规则编码
     * @param operator 操作人
     */
    void delete(String ruleCode, String operator);
}
