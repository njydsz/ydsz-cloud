/**
 * 平台自研弹性容错引擎（熔断器）。
 *
 * <p>内网项目不引入第三方弹性库（Resilience4j 等竞品），本包提供平台唯一的熔断器标准实现，
 * 供各业务模块与公共组件（网关 / Feign / 消息通道 / 规则引擎 / 搜索 / 日志通道）复用。
 *
 * <p>能力矩阵：
 *
 * <ul>
 *   <li>{@link com.njydsz.common.safe.resilience.CircuitBreaker}：三态 + FORCED_OPEN
 *       状态机（CAS 保护），支持慢调用统计、失败判定谓词、事件总线
 *   <li>{@link com.njydsz.common.safe.resilience.CircuitBreakerConfig}：COUNT_BASED /
 *       TIME_BASED 双模式滑动窗口配置
 *   <li>{@link com.njydsz.common.safe.resilience.CircuitBreakerRegistry}：按名称注册与共享
 *       （Spring 场景注册为共享 Bean）
 *   <li>{@link com.njydsz.common.safe.resilience.CircuitBreakerEvents}：状态变更 / 成功 /
 *       失败事件订阅（对接指标导出）
 *   <li>{@link com.njydsz.common.safe.resilience.CallNotPermittedException}：熔断拒绝异常
 *       （响应式场景三段式手动记录的信号源）
 * </ul>
 *
 * <p>设计决策见 {@code docs/ADR-0004-resilience-self-hosted.md}。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
package com.njydsz.common.safe.resilience;
