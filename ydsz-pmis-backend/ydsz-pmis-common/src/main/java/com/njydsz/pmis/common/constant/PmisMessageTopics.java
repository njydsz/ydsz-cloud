package com.njydsz.pmis.common.constant;

/**
 * PMIS RocketMQ Topic / ConsumerGroup 常量定义
 *
 * <p>集中管理所有 RocketMQ Topic 与消费组名称，避免散落在各模块的字面量。
 * 各模块（system / project / workflow）通过引用本类常量发送/消费消息，
 * 保证 Producer 与 Consumer 的 Topic / Group 拼写一致。
 *
 * <h3>Topic 命名规范</h3>
 * <ul>
 *   <li>业务消息：{@code pmis-message-topic}（通道发送：SMS/EMAIL/PUSH/IN_APP/WEBHOOK）</li>
 *   <li>工作流事件：{@code pmis-workflow-event-topic}（任务创建/通过/驳回等）</li>
 *   <li>预算告警：{@code pmis-budget-alert-topic}（YELLOW/RED 告警分发）</li>
 *   <li>死信队列：{@code %DLQ%pmis-message-consumer}（RocketMQ 内置 DLQ 前缀）</li>
 * </ul>
 *
 * <h3>DLQ 命名规则</h3>
 * <p>RocketMQ 死信 Topic 命名为 {@code %DLQ%<consumerGroup>}，其中 {@code %DLQ%} 为
 * RocketMQ 内置死信前缀。订阅该 Topic 即可消费对应消费组的死信消息。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public final class PmisMessageTopics {

    private PmisMessageTopics() {
        // 常量类，禁止实例化
    }

    // ==================== Topic ====================

    /** 通知消息 Topic：通道发送（SMS/EMAIL/PUSH/IN_APP/WEBHOOK） */
    public static final String TOPIC_MESSAGE = "pmis-message-topic";

    /** 工作流事件 Topic：任务创建/通过/驳回/转办/委派/催办/超时/实例终止等 */
    public static final String TOPIC_WORKFLOW_EVENT = "pmis-workflow-event-topic";

    /** 预算告警 Topic：YELLOW(80%)/RED(95%) 阈值告警分发 */
    public static final String TOPIC_BUDGET_ALERT = "pmis-budget-alert-topic";

    // ==================== Consumer Group ====================

    /** 通知消息消费组 */
    public static final String GROUP_MESSAGE = "pmis-message-consumer";

    /** 工作流事件消费组 */
    public static final String GROUP_WORKFLOW_EVENT = "pmis-workflow-event-consumer";

    /** 预算告警消费组 */
    public static final String GROUP_BUDGET_ALERT = "pmis-budget-alert-consumer";

    // ==================== DLQ ====================

    /**
     * 通知消息死信 Topic：{@code %DLQ%pmis-message-consumer}
     *
     * <p>RocketMQ 在 maxReconsumeTimes 次重试失败后，自动将消息投递到该 Topic。
     * 订阅该 Topic 即可消费死信消息，避免消息丢失。
     */
    public static final String DLQ_MESSAGE = "%DLQ%" + GROUP_MESSAGE;

    /** 死信消费组（独立于业务消费组，避免循环死信） */
    public static final String GROUP_DLQ_MESSAGE = "pmis-message-dlq-consumer";
}
