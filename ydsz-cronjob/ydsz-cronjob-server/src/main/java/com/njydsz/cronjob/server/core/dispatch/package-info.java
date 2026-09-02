/**
 * 任务派发核心包。
 *
 * <p>包含任务派发器、锁管理、配额管理、告警与事件、指标记录等核心组件， 负责任务的调度、执行、状态跟踪和异常处理。
 *
 * <p>核心类：
 *
 * <ul>
 *   <li>{@link com.njydsz.cronjob.server.core.dispatch.DefaultTaskDispatcher} - 默认任务派发器
 *   <li>{@link com.njydsz.cronjob.server.core.dispatch.JobExecutionLockHelper} - 任务执行锁管理辅助类
 *   <li>{@link com.njydsz.cronjob.server.core.dispatch.JobExecutionQuotaHelper} - 任务执行配额管理辅助类
 *   <li>{@link com.njydsz.cronjob.server.core.dispatch.JobExecutionAlertHelper} - 任务执行告警与事件辅助类
 *   <li>{@link com.njydsz.cronjob.server.core.dispatch.JobExecutionMetricsHelper} - 任务执行指标记录辅助类
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
package com.njydsz.cronjob.server.core.dispatch;
