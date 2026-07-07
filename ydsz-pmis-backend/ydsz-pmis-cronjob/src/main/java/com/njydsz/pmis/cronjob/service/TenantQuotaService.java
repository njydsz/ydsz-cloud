package com.njydsz.pmis.cronjob.service;

import com.njydsz.pmis.cronjob.entity.TenantQuotaDO;

/**
 * 租户级配额服务（P7-2）。
 *
 * <p>提供任务数/并发数/日执行量配额检查能力，防止 noisy neighbor 问题。
 * 默认禁用（{@code pmis.cronjob.quota.enabled=false}），启用后在任务创建/派发时自动校验。
 *
 * <h3>配额优先级</h3>
 * <ol>
 *   <li>DB 记录（{@code pmis_tenant_quota} 表对应租户的配置）</li>
 *   <li>全局默认（{@code pmis.cronjob.quota.default-max-*} 配置）</li>
 *   <li>Unlimited（未启用配额检查或上限为 null）</li>
 * </ol>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface TenantQuotaService {

    /**
     * 获取指定租户的配额记录。
     *
     * @param tenantId 租户 ID
     * @return 配额记录；不存在时返回 null
     */
    TenantQuotaDO getQuota(String tenantId);

    /**
     * 检查租户是否可以创建新任务（任务数配额检查）。
     *
     * <p>当 {@code pmis.cronjob.quota.enabled=false} 时直接返回（不检查）。
     * 当租户配额记录不存在时，使用全局默认 {@code defaultMaxJobs}。
     *
     * @param tenantId 租户 ID
     * @throws com.njydsz.pmis.common.exception.BizException 当超过任务数上限时抛出
     *         {@link com.njydsz.pmis.common.api.BizErrorCode#QUOTA_EXCEEDED}
     */
    void checkJobQuota(String tenantId);

    /**
     * 检查租户是否可以启动新的并发执行（并发数配额检查，P7-3 实现）。
     *
     * <p>当前阶段（P7-2）仅做接口预留，始终通过。P7-3 将通过 Redis 实时计数器实现。
     *
     * @param tenantId 租户 ID
     * @throws com.njydsz.pmis.common.exception.BizException 当超过并发上限时抛出
     *         {@link com.njydsz.pmis.common.api.BizErrorCode#QUOTA_EXCEEDED}
     */
    void checkConcurrentQuota(String tenantId);

    /**
     * 检查租户是否可以执行新任务（日执行量配额检查，P7-3 实现）。
     *
     * <p>当前阶段（P7-2）仅做接口预留，始终通过。P7-3 将通过 Redis 日计数器实现。
     *
     * @param tenantId 租户 ID
     * @throws com.njydsz.pmis.common.exception.BizException 当超过日执行量上限时抛出
     *         {@link com.njydsz.pmis.common.api.BizErrorCode#QUOTA_EXCEEDED}
     */
    void checkDailyExecutionQuota(String tenantId);
}
