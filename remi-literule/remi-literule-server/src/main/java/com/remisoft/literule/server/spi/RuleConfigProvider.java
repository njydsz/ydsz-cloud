package com.remisoft.literule.server.spi;

import java.util.List;

import com.remisoft.literule.api.RuleDefinition;
import com.remisoft.literule.api.RuleEnvironment;

/**
 * 规则配置提供者接口（SPI）
 *
 * <p>由消费方（如 execution 模块）提供实现，从数据库/配置中心加载规则定义。
 * literule 模块本身不依赖任何持久层实现，通过此接口反转依赖。
 *
 * @since 1.0.0
 * @author remi-team
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
     * @since 1.0.0
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
     * @since 1.0.0
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

    /**
     * 加载指定租户和环境下启用的规则定义（1.6.0 起，P1-5 多环境隔离）
     *
     * <p>默认实现调用 {@link #loadEnabledRulesByTenant(String)} 后在内存按 environment 过滤：
     * <ul>
     *   <li>规则的 environment 为 {@link RuleEnvironment#DEFAULT "default"} 时，匹配任何环境（向后兼容）</li>
     *   <li>规则的 environment 非 "default" 时，必须与 {@code environment} 完全匹配</li>
     * </ul>
     * 性能敏感场景应覆写为带 {@code WHERE tenant_id = ? AND (environment = 'default' OR environment = ?)}
     * 的 SQL 查询。
     *
     * @param tenantId    租户 ID
     * @param environment 环境标识（dev/staging/prod/default）
     * @return 该租户下匹配环境的启用规则定义列表
     * @since 1.0.0
     */
    default List<RuleDefinition> loadEnabledRulesByEnv(String tenantId, String environment) {
        List<RuleDefinition> all = loadEnabledRulesByTenant(tenantId);
        if (environment == null || environment.isBlank() || RuleEnvironment.DEFAULT.equals(environment)) {
            return all;
        }
        return all.stream()
                .filter(r -> {
                    String ruleEnv = r.getEnvironment();
                    return ruleEnv == null || ruleEnv.isBlank()
                            || RuleEnvironment.DEFAULT.equals(ruleEnv)
                            || environment.equals(ruleEnv);
                })
                .toList();
    }
}
