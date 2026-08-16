/**
 * 线程池执行器实现。
 *
 * <p>包含 {@link com.njydsz.common.thread.executor.MeteredThreadPoolExecutor} 可观测线程池执行器， 提供
 * Micrometer 指标自动注册与慢任务检测能力。
 *
 * <p>适用于需要在编程式创建线程池时仍能采集指标的场景。
 *
 * @author ydsz-team
 * @since 1.5.0
 */
package com.njydsz.common.thread.executor;
