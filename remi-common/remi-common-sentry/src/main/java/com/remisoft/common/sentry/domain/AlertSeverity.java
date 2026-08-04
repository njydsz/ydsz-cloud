package com.remisoft.common.sentry.domain;

/**
 * 告警严重级别
 *
 * <p>对应 Alertmanager 路由策略：
 * <ul>
 *   <li>P0 - 电话语音 + 钉钉（立即通知）</li>
 *   <li>P1 - 钉钉通知（5 分钟聚合）</li>
 *   <li>P2 - 邮件通知（30 分钟聚合）</li>
 *   <li>P3 - 仅记录，不通知</li>
 * </ul>
 *
 * @author remi-team
 * @since 1.0.0
 */
public enum AlertSeverity {

    /** 立即电话 + 钉钉：服务不可用、数据库连接失败 */
    P0,

    /** 钉钉通知：业务指标严重异常、错误率激增 */
    P1,

    /** 邮件通知：性能劣化、资源水位告警 */
    P2,

    /** 仅记录：业务趋势告警 */
    P3
}
