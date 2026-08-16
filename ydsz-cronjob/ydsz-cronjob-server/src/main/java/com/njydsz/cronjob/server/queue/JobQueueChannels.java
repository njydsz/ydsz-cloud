package com.njydsz.cronjob.server.queue;

import com.njydsz.common.queue.constant.QueueChannels;

/**
 * 定时任务消息队列通道常量（语义别名）。
 *
 * <p>定义 cronjob 模块使用的所有消息队列通道名称。
 * 通过 common-queue 实现事件驱动的任务调度和任务执行结果分发。
 *
 * <p><b>注意：</b>所有通道值引用自 {@link QueueChannels}（统一注册中心），
 * 禁止在此类中重复定义字符串常量。新增通道请到 {@code QueueChannels} 中注册。
 *
 * <p><b>通道说明：</b>
 * <ul>
 *   <li>{@link #JOB_EVENT_TRIGGER} - 事件驱动调度通道（消费方：cronjob 模块）</li>
 *   <li>{@link #JOB_RESULT} - 任务执行结果通道（生产方：cronjob 模块）</li>
 *   <li>{@link #JOB_ALERT} - 任务告警事件通道（生产方：cronjob 模块）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class JobQueueChannels {

    private JobQueueChannels() {
    }

    /**
     * 事件驱动调度通道
     *
     * <p>其他服务向此通道发送消息以触发定时任务执行。
     * 消息体格式：{@code {"jobKey":"...", "msgId":"...", "payload":"..."}}
     */
    public static final String JOB_EVENT_TRIGGER = QueueChannels.JOB_EVENT_TRIGGER;

    /**
     * 任务执行结果通道
     *
     * <p>cronjob 模块将任务执行结果发布到此通道，供其他服务消费。
     * 消息体格式：{@code {"jobId":"...", "jobKey":"...", "success":true, "logId":"..."}}
     */
    public static final String JOB_RESULT = QueueChannels.JOB_RESULT;

    /**
     * 任务告警事件通道
     *
     * <p>cronjob 模块将告警事件发布到此通道，供通知服务消费并派发多通道通知。
     */
    public static final String JOB_ALERT = QueueChannels.JOB_ALERT;
}
