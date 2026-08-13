/**
 * ydzz-common-thread 统一线程池管理模块。
 *
 * <p><b>模块定位：</b>提供基于 Spring Boot 自动装配的统一线程池管理能力，
 * 支持按业务隔离配置、Micrometer 指标暴露、优雅关闭、运行时动态调参。
 *
 * <p><b>与 ydzs-common-util 并发工具的区别：</b>
 * <ul>
 *   <li>本模块（thread）：面向 Spring Boot 配置驱动的线程池生命周期管理</li>
 *   <li>common-util：面向代码级的并发工具（{@code MeteredThreadPoolExecutor}、
 *       {@code BoundedVirtualThreadScheduler}、{@code Disruptor} 等）</li>
 * </ul>
 *
 * <p><b>v1.3.0 新特性：</b>
 * <ul>
 *   <li>{@code ThreadPoolMetrics} / {@code VirtualThreadMetrics} 自动注册</li>
 *   <li>{@code MeteredRejectedHandler} 自动包装拒绝策略</li>
 *   <li>{@code TaskDecorator} 配置化上下文传播</li>
 *   <li>{@code ThreadPoolHotUpdateListener} 运行时动态调整</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
package com.njydsz.common.thread;
