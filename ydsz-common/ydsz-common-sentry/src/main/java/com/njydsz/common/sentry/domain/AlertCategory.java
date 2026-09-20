package com.njydsz.common.sentry.domain;

/**
 * 告警分类枚举。
 *
 * <p>对标 Prometheus Alertmanager 标准分类，便于 APM 后端按类别路由通知策略：
 *
 * <ul>
 *   <li>AVAILABILITY - 服务可用性告警（宕机、健康检查失败、熔断器打开）
 *   <li>PERFORMANCE - 性能告警（响应超时、SLA 违反、队列积压）
 *   <li>BUSINESS - 业务告警（失败率激增、业务指标异常）
 *   <li>SECURITY - 安全告警（认证异常、权限越权、暴力破解）
 *   <li>CAPACITY - 容量告警（磁盘满、内存高水位、连接池耗尽）
 * </ul>
 *
 * <p>26.09.20 新增。
 *
 * @author ydsz-team
 * @since 26.09.20
 */
public enum AlertCategory {

  /** 服务可用性：宕机、健康检查失败、熔断器打开 */
  AVAILABILITY,

  /** 性能劣化：响应超时、SLA 违反、队列积压 */
  PERFORMANCE,

  /** 业务指标异常：失败率激增、业务逻辑异常 */
  BUSINESS,

  /** 安全事件：认证异常、权限越权、暴力破解 */
  SECURITY,

  /** 容量告警：磁盘满、内存高水位、连接池耗尽 */
  CAPACITY
}
