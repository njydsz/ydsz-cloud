package com.njydsz.workflow.server.queue;

import com.njydsz.common.queue.constant.QueueChannels;

/**
 * 工作流消息队列通道常量（语义别名）。
 *
 * <p>定义工作流模块使用的所有消息队列通道名称，用于跨服务事件分发。
 * 通过 common-queue 的 IMessagePublisher 发布事件，其他服务（如 project 模块）
 * 可订阅这些通道实现跨服务异步通信。
 *
 * <p><b>注意：</b>所有通道值引用自 {@link QueueChannels}（统一注册中心），
 * 禁止在此类中重复定义字符串常量。新增通道请到 {@code QueueChannels} 中注册。
 *
 * <p><b>通道说明：</b>
 * <ul>
 *   <li>{@link #FLOW_EVENT} - 工作流生命周期事件（任务创建/完成、实例启动/完成/驳回/终止等）</li>
 *   <li>{@link #FLOW_TIMEOUT} - 流程超时事件（供 cronjob 模块消费触发超时处理任务）</li>
 * </ul>
 *
 * @since 1.0.0
 * @author ydsz-team
 */
public final class FlowQueueChannels {

    private FlowQueueChannels() {
    }

    /**
     * 工作流生命周期事件通道
     *
     * <p>事件类型包括：INSTANCE_STARTED, INSTANCE_COMPLETED, INSTANCE_REJECTED,
     * INSTANCE_TERMINATED, INSTANCE_RECALLED, TASK_CREATED, TASK_COMPLETED,
     * TASK_URGED, TASK_TRANSFERRED, TASK_DELEGATED, TASK_TIMEOUT 等。
     */
    public static final String FLOW_EVENT = QueueChannels.FLOW_EVENT;

    /**
     * 流程超时事件通道
     *
     * <p>供 cronjob 模块消费，触发超时处理任务。
     */
    public static final String FLOW_TIMEOUT = QueueChannels.FLOW_TIMEOUT;
}
