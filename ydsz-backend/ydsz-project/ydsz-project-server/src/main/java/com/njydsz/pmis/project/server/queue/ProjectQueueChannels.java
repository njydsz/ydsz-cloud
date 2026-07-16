package com.njydsz.project.server.queue;

/**
 * 项目管理消息队列通道常量
 *
 * <p>定义 project 模块使用的所有消息队列通道名称。
 * 通过 common-queue 实现项目生命周期事件的跨服务分发和工作流事件的消费。
 *
 * <p><b>通道说明：</b>
 * <ul>
 *   <li>{@link #PROJECT_BUDGET_ALERT} - 预算告警事件通道（生产方：project 模块）</li>
 *   <li>{@link #PROJECT_CHANGE} - 项目变更事件通道（生产方：project 模块）</li>
 *   <li>{@link #FLOW_EVENT} - 工作流事件通道（消费方：project 模块，与 workflow 模块的 FlowQueueChannels.FLOW_EVENT 对应）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.2.0
 */
public final class ProjectQueueChannels {

    private ProjectQueueChannels() {
    }

    /**
     * 预算告警事件通道
     *
     * <p>project 模块将预算告警事件发布到此通道，供通知服务/预警中心消费。
     * 消息体包含 initiationId、projectCode、budget、ratio、level 等字段。
     */
    public static final String PROJECT_BUDGET_ALERT = "ydsz:project:budget-alert";

    /**
     * 项目变更事件通道
     *
     * <p>project 模块将项目变更事件发布到此通道，供报表服务/EVM 引擎消费。
     */
    public static final String PROJECT_CHANGE = "ydsz:project:change";

    /**
     * 工作流事件通道（消费方）
     *
     * <p>与 workflow 模块的 {@code FlowQueueChannels.FLOW_EVENT} 对应，
     * project 模块订阅此通道消费工作流生命周期事件，实现项目状态联动。
     */
    public static final String FLOW_EVENT = "ydsz:flow:event";
}
