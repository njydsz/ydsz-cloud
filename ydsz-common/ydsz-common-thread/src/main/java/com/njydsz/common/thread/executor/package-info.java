/**
 * 线程池执行器实现。
 *
 * <p>提供可观测线程池执行器与工厂，支持 Micrometer 指标自动注册与慢任务检测能力。
 *
 * <p>适用于需要在编程式创建线程池时仍能采集指标的场景。统一线程池创建入口见
 * {@link com.njydsz.common.thread.config.ThreadPoolExecutorFactory}。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
package com.njydsz.common.thread.executor;
