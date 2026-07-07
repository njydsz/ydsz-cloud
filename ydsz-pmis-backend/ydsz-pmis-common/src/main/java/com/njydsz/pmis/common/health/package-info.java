/**
 * 自定义健康检查层。
 *
 * <p>为 Spring Boot Actuator 提供扩展健康指示器，覆盖 PMIS 依赖的关键中间件。
 * 健康检查结果暴露在 {@code /actuator/health} 端点，被 Kubernetes liveness / readiness 探针、
 * 负载均衡器、SLA 监控等消费。
 *
 * <h3>健康检查清单</h3>
 * <ul>
 *   <li>{@link com.njydsz.pmis.common.health.RedisHealthIndicator}    - Redis 8 连接 / 集群拓扑</li>
 *   <li>{@link com.njydsz.pmis.common.health.DatabaseHealthIndicator} - PostgreSQL 连接池 / 复制延迟</li>
 * </ul>
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li>健康检查超时时间 ≤ 2s，避免拖慢探针</li>
 *   <li>检查失败立即返回降级状态，不抛异常</li>
 *   <li>所有健康指标通过 Micrometer 上报 Prometheus</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
package com.njydsz.pmis.common.health;
