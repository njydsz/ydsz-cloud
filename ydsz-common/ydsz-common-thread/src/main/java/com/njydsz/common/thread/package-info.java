/**
 * ydsz-common-thread 统一线程池管理模块。
 *
 * <p><b>模块定位：</b>面向 Spring Boot 配置驱动的线程池生命周期管理，
 * 支持按业务隔离配置、Micrometer 指标暴露、优雅关闭、运行时动态调参。
 *
 * <p><b>与 ydsz-common-util 的职责边界：</b>
 * <ul>
 *   <li>本模块（thread）：提供 Spring 容器托管的线程池自动配置与监控。
 *       通过 {@code ydsz.thread.pools} 配置的线程池会被注册为 Spring Bean，
 *       自动绑定 Micrometer 指标、健康检查与优雅关闭钩子</li>
 *   <li>common-util：提供编程式并发工具（{@code ExecutorUtils} 静态工厂、
 *       {@code ThreadPoolMonitor} 等）。
 *       适用于非 Spring 场景、短生命周期线程池或需要手动注册监控的场景</li>
 * </ul>
 *
 * <p><b>v1.4.0 变更：</b>
 * <ul>
 *   <li>TimedTaskDecorator 改用不可变包装对象传递时间戳，修复潜在的 threadId 串扰风险</li>
 *   <li>ThreadHealthIndicator 收紧扫描范围，仅检查 ydzz 管理的线程池，避免误报</li>
 *   <li>移除虚拟线程池的 rejected 指标（JDK 21 虚拟线程从不拒绝，属不可达代码）</li>
 *   <li>热更新监听器纳入自动配置体系（{@code ydsz-common-thread} 提供
 *       {@link com.njydsz.common.thread.config.ThreadPoolHotUpdateAutoConfiguration}）</li>
 *   <li>新增池级别慢任务阈值（{@code slow-task-threshold-ms}）和详细指标开关
 *      （{@code enable-detailed-metrics}）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
package com.njydsz.common.thread;
