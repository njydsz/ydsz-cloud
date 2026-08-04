package com.remisoft.common.auth.metrics;

/**
 * 权限校验（Authorization）统一指标采集契约。
 *
 * <p>规范化「权限校验」核心指标，包括权限通过/拒绝、缓存命中、权限校验耗时、Redis 可用状态。
 * 由 {@link AuthMetricsCollector} 实现，供 {@code RbacPermissionEvaluator} 等权限校验组件调用。
 *
 * <p>认证（Authentication）相关指标见 {@link AuthMetrics}。
 *
 * @author remi-team
 * @since 1.0.0
 * @see AuthMetrics
 * @see AuthMetricsCollector
 */
public interface PermissionMetrics {

    /**
     * 记录一次权限校验通过。
     *
     * @param permissionType 权限类型（MENU/BUTTON/API/ROLE）
     */
    void recordPermissionAllow(String permissionType);

    /**
     * 记录一次权限校验拒绝，并写入安全审计日志。
     *
     * @param userId              用户 ID（可为 null）
     * @param permissionType      权限类型（MENU/BUTTON/API/ROLE）
     * @param requiredPermissions 缺少的权限
     * @param resource            资源路径（可为 null）
     */
    void recordPermissionDeny(String userId, String permissionType,
                              String requiredPermissions, String resource);

    /**
     * 记录权限缓存命中。
     */
    void recordCacheHit();

    /**
     * 记录权限缓存未命中。
     */
    void recordCacheMiss();

    /**
     * 记录权限校验耗时。
     *
     * @param nanos 耗时（纳秒）
     */
    void recordCheckTime(long nanos);

    /**
     * 更新 Redis 可用状态（Gauge）。
     *
     * @param available Redis 是否可用
     */
    void updateRedisAvailable(boolean available);
}
