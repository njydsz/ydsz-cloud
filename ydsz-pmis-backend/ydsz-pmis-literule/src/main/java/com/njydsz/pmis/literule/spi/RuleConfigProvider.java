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

    /**
     * 加载指定租户下全部启用的规则定义（1.5.1 起支持物理隔离）
     *
     * <p>默认实现调用 {@link #loadEnabledRules()} 后在内存按 tenantId 过滤，
     * 性能敏感场景应覆写为带 {@code WHERE tenant_id = ?} 的 SQL 查询。
     *
     * @param tenantId 租户 ID
     * @return 该租户下启用的规则定义列表
     * @since 1.5.1
     */
    default List<RuleDefinition> loadEnabledRulesByTenant(String tenantId) {
        List<RuleDefinition> all = loadEnabledRules();
        if (tenantId == null || tenantId.isBlank()) {
            return all;
        }
        return all.stream()
                .filter(r -> tenantId.equals(r.getTenantId()))
                .toList();
    }

    /**
     * 加载指定租户下全部规则定义（含禁用，1.5.1 起支持物理隔离）
     *
     * <p>默认实现调用 {@link #loadAllRules()} 后在内存按 tenantId 过滤。
     *
     * @param tenantId 租户 ID
     * @return 该租户下全部规则定义列表
     * @since 1.5.1
     */
    default List<RuleDefinition> loadAllRulesByTenant(String tenantId) {
        List<RuleDefinition> all = loadAllRules();
        if (tenantId == null || tenantId.isBlank()) {
            return all;
        }
        return all.stream()
                .filter(r -> tenantId.equals(r.getTenantId()))
                .toList();
    }
}
