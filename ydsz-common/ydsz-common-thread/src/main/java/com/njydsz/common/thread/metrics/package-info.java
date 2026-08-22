/**
 * Micrometer 指标绑定器。
 *
 * <p>包含以下核心类：
 *
 * <ul>
 *   <li>{@link com.njydsz.common.thread.metrics.ThreadPoolMetrics} — 平台线程池 8 项指标绑定
 *   <li>{@link com.njydsz.common.thread.metrics.VirtualThreadMetrics} — 虚拟线程池 4 项指标绑定
 *   <li>{@link com.njydsz.common.thread.metrics.MeteredRejectedHandler} — 带指标的拒绝策略装饰器
 * </ul>
 *
 * @since 1.0.0
 */
package com.njydsz.common.thread.metrics;
