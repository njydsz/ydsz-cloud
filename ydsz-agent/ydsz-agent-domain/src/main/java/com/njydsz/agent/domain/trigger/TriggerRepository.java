package com.njydsz.agent.domain.trigger;

import java.util.List;
import java.util.Optional;

/**
 * 触发器仓储接口。
 *
 * <p>定义触发器的持久化操作，实现层位于 infra 模块。
 * 支持按租户、触发类型、启用状态等维度查询。</p>
 *
 * @author ydsz-agent
 * @since 26.09.01
 */
public interface TriggerRepository {

    /**
     * 保存触发器配置。
     *
     * @param trigger 触发器聚合根
     * @return 保存后的触发器实例
     */
    AgentTrigger save(AgentTrigger trigger);

    /**
     * 根据触发器 ID 查询。
     *
     * @param triggerId 触发器唯一标识
     * @return 触发器实例（可能为空）
     */
    Optional<AgentTrigger> findById(String triggerId);

    /**
     * 查询租户下所有启用的触发器。
     *
     * @param tenantId 租户 ID
     * @return 启用的触发器列表
     */
    List<AgentTrigger> findEnabledByTenant(String tenantId);

    /**
     * 根据触发类型查询。
     *
     * @param tenantId    租户 ID
     * @param triggerType 触发类型
     * @return 匹配的触发器列表
     */
    List<AgentTrigger> findByTenantAndType(String tenantId, TriggerType triggerType);

    /**
     * 查询所有启用的定时触发器（用于调度任务扫描）。
     *
     * @return 所有启用的 CRON 类型触发器
     */
    List<AgentTrigger> findAllEnabledCronTriggers();

    /**
     * 删除触发器。
     *
     * @param triggerId 触发器 ID
     */
    void delete(String triggerId);

    /**
     * 统计租户下触发器数量。
     *
     * @param tenantId 租户 ID
     * @return 触发器数量
     */
    long countByTenant(String tenantId);
}
