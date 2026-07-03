package com.njydsz.pmis.literule.spi;

import com.njydsz.pmis.literule.api.ScriptDefinition;

import java.util.List;

/**
 * 脚本规则配置提供者接口（SPI）
 *
 * <p>由消费方提供实现，从数据库加载脚本规则定义。
 *
 * @author ydsz-pmis-team
 * @since 1.4.0
 */
public interface ScriptConfigProvider {

    /**
     * 加载全部启用的脚本规则
     *
     * @return 启用的脚本规则列表
     */
    List<ScriptDefinition> loadEnabledScripts();

    /**
     * 加载全部脚本规则（含禁用）
     *
     * @return 全部脚本规则列表
     */
    List<ScriptDefinition> loadAllScripts();

    /**
     * 保存脚本规则
     *
     * @param definition 脚本规则定义
     * @param operator   操作人
     * @return 保存后的定义
     */
    ScriptDefinition save(ScriptDefinition definition, String operator);

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
     * @return 脚本规则定义；不存在返回 null
     */
    ScriptDefinition findByCode(String ruleCode);

    /**
     * 删除脚本规则
     *
     * @param ruleCode 规则编码
     * @param operator 操作人
     */
    void delete(String ruleCode, String operator);
}
