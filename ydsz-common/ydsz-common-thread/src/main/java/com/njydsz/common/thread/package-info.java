/**
 * ydsz-common-thread 统一线程管理模块。
 *
 * <p><b>模块定位：</b>面向 Spring Boot 配置驱动的线程池生命周期管理， 支持按业务隔离配置、Micrometer 指标暴露、优雅关闭、运行时动态调参。
 * 同时提供编程式线程池工厂与可观测执行器。
 *
 * <p><b>与 ydsz-common-util 的职责边界：</b>
 *
 * <ul>
 *   <li>本模块（thread）：集配置驱动托管、编程式工厂、可观测执行器于一体， 是线程池管理能力的唯一归属模块
 *   <li>common-util：仅保留限流、重试等通用并发工具（{@code RateLimiter}、{@code RetryUtils}）， 不再包含线程池创建与监控能力
 * </ul>
 *
 * <p><b>v1.5.0 变更：</b>
 *
 * <ul>
 *   <li>从 ydsz-common-util 迁入 {@code ExecutorUtils} 编程式线程池工厂
 *   <li>线程池管理能力统一收归本模块，ydsz-common-util 不再提供线程池相关能力
 * </ul>
 *
 * <p><b>v1.4.0 变更：</b>
 *
 * <ul>
 *   <li>TimedTaskDecorator 改用不可变包装对象传递时间戳，修复潜在的 threadId 串扰风险
 *   <li>ThreadHealthIndicator 收紧扫描范围，仅检查 ydzz 管理的线程池，避免误报
 *   <li>移除虚拟线程池的 rejected 指标（JDK 21 虚拟线程从不拒绝，属不可达代码）
 *   <li>热更新监听器纳入自动配置体系（{@code ydsz-common-thread} 提供 {@link
 *       com.njydsz.common.thread.config.ThreadPoolHotUpdateAutoConfiguration}）
 *   <li>新增池级别慢任务阈值（{@code slow-task-threshold-ms}）和详细指标开关 （{@code enable-detailed-metrics}）
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
package com.njydsz.common.thread;
