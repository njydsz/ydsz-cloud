/**
 * 线程池指标采集与慢任务追踪体系。
 *
 * <p>包含以下核心类：
 *
 * <h2>指标绑定器</h2>
 * <ul>
 *   <li>{@link com.njydsz.common.thread.metrics.ThreadPoolMetrics} — 平台线程池 Micrometer 指标（核心 5 项 + 可选详细指标）
 *   <li>{@link com.njydsz.common.thread.metrics.VirtualThreadMetrics} — 虚拟线程池指标（submitted / completed Gauge）
 *   <li>{@link com.njydsz.common.thread.metrics.ThreadPoolTimerMetrics} — 耗时 Timer 指标 + 慢任务计数器（带缓存优化）
 *   <li>{@link com.njydsz.common.thread.metrics.ThreadPoolRegistryMetrics} — 注册中心级聚合指标 + 增量刷新支持
 * </ul>
 *
 * <h2>装饰器与追踪</h2>
 * <ul>
 *   <li>{@link com.njydsz.common.thread.metrics.TimedTaskDecorator} — 耗时追踪 + 慢任务采样记录（SlowTaskRecord 队列）
 *   <li>{@link com.njydsz.common.thread.metrics.MeteredRejectedHandler} — 带拒绝计数的拒绝策略包装器
 *   <li>{@link com.njydsz.common.thread.metrics.MeteredVirtualExecutorService} — 虚拟线程池计数包装器
 *   <li>{@link com.njydsz.common.thread.metrics.SlowTaskRecord} — 慢任务执行记录（不可变值对象）
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
package com.njydsz.common.thread.metrics;
