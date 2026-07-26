package com.njydsz.common.auth.metrics;

/**
 * 认证（Authentication）统一指标采集契约。
 *
 * <p>本接口规范化「认证」核心指标的采集方法，使 Web 端、App 端、
 * 网关端等不同入口可以共享同一套指标语义，便于跨端监控聚合与告警。
 *
 * <p>权限校验（Authorization）相关指标见 {@link PermissionMetrics}。
 *
 * <p><b>实现要点：</b>
 * <ul>
 *   <li>实现类应保证线程安全（Micrometer Counter/Timer 本身线程安全）</li>
 *   <li>{@code userType} 建议取值：{@code web} / {@code app} / {@code openapi} / {@code service} / {@code unknown}</li>
 *   <li>{@code reason} 建议取值：{@code missing_token} / {@code invalid_token} / {@code expired_token}
 *       / {@code revoked_token} / {@code signature_mismatch} / {@code unknown}</li>
 *   <li>所有方法允许传入 {@code null}，实现类应做防御处理（降级为 {@code "unknown"}）</li>
 * </ul>
 *
 * <p><b>不同实现的指标命名前缀：</b>
 * <ul>
 *   <li>{@link AuthMetricsCollector} — {@code auth.login.*}（认证授权模块自身）</li>
 *   <li>{@code AppMetrics} — {@code app.auth.*}（App 端）</li>
 *   <li>{@code WebMetrics} — {@code web.auth.*}（Web 端，未实现本接口，仅做参考）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see AuthMetricsCollector
 * @see PermissionMetrics
 */
public interface AuthMetrics {

    /**
     * 记录一次认证成功。
     *
     * @param userType       用户类型（web/app/openapi/service/unknown）
     * @param durationNanos  认证耗时（纳秒）
     */
    void recordAuthSuccess(String userType, long durationNanos);

    /**
     * 记录一次认证失败。
     *
     * @param userType       用户类型（web/app/openapi/service/unknown）
     * @param reason         失败原因（missing_token/invalid_token/expired_token/revoked_token/signature_mismatch/unknown）
     * @param durationNanos  认证耗时（纳秒）
     */
    void recordAuthFailure(String userType, String reason, long durationNanos);

    /**
     * 记录一次认证跳过（如请求命中白名单、Service 跳过等场景）。
     *
     * @param reason 跳过原因（whitelist/skip_service/disabled/unknown）
     */
    void recordAuthSkip(String reason);
}
