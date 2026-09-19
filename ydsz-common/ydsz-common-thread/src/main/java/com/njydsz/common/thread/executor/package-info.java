/**
 * 线程池执行器实现。
 *
 * <p>提供可观测线程池执行器与工厂，支持 Micrometer 指标自动注册与慢任务检测能力。
 *
 * <h2>ExecutorCreator 体系（P2-5）</h2>
 *
 * <p>通过 {@link com.njydsz.common.thread.executor.ExecutorCreator} 接口统一平台/虚拟线程池创建策略，
 * 支持后续扩展新线程池类型（如结构化并发池、GraalVM 轻量线程池）而无需修改注册器分支逻辑。
 *
 * <ul>
 *   <li>{@link com.njydsz.common.thread.executor.PlatformExecutorCreator} — 平台线程池创建
 *   <li>{@link com.njydsz.common.thread.executor.VirtualExecutorCreator} — 虚拟线程池创建
 * </ul>
 *
 * <p>适用于需要在编程式创建线程池时仍能采集指标的场景。统一线程池创建入口见
 * {@link com.njydsz.common.thread.config.ThreadPoolExecutorFactory}。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
package com.njydsz.common.thread.executor;
