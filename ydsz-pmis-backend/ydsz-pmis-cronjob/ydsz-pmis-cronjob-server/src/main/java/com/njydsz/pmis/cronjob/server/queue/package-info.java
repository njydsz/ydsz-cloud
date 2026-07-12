/**
 * 定时任务消息队列集成
 *
 * <p>通过 common-queue 模块实现事件驱动的任务调度和任务执行结果分发。
 *
 * <p><b>核心组件：</b>
 * <ul>
 *   <li>{@link com.njydsz.pmis.cronjob.server.queue.JobQueueChannels} - 队列通道常量</li>
 *   <li>{@link com.njydsz.pmis.cronjob.server.queue.JobEventQueueSubscriber} - 事件驱动调度订阅者</li>
 *   <li>{@link com.njydsz.pmis.cronjob.server.queue.JobResultQueuePublisher} - 任务结果发布者</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.2.0
 */
package com.njydsz.pmis.cronjob.server.queue;
