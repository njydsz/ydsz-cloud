package com.njydsz.cronjob.server.core;

/**
 * 任务完成事件。
 *
 * <p>当定时任务执行完成时由 {@code DefaultTaskDispatcher} 发布， 各监听器（{@code DagInstanceExecutor}、 {@code
 * JobResultQueuePublisher}）异步消费此事件。
 *
 * @param jobId 任务 ID
 * @param jobKey 任务 Key
 * @param success 是否成功
 * @param logId 日志 ID
 * @author ydsz-team
 * @since 26.09.01
 */
public record TaskCompletedEvent(String jobId, String jobKey, boolean success, String logId) {}
