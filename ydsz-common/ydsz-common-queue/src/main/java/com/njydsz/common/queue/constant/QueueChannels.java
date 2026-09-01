package com.njydsz.common.queue.constant;

/**
 * 跨模块消息通道常量注册中心
 *
 * <p>集中管理所有业务模块的 MQ 通道名称常量，消除各模块重复定义。 通道全局唯一，作为 MQ 中间件的命名契约。
 *
 * <p><b>通道命名规范</b>：{@code ydsz:{模块}:{用途}}
 *
 * <ul>
 *   <li>单词分隔符使用 {@code :}（冒号），与 RocketMQ/Kafka Topic 命名体系兼容
 *   <li>单词内部使用 {@code -}（短横线），符合 DNS 友好的命名习惯
 * </ul>
 *
 * <p><b>新增通道步骤</b>：
 *
 * <ol>
 *   <li>在本类对应分组中定义通道常量
 *   <li>更新对应模块的 {@code application.yml} 配置
 *   <li>在使用方的 {@code ChannelConstants} 中引用本类常量
 * </ol>
 *
 * <p>各业务模块可以保留本地的 {@code XxxQueueChannels} 类作为语义别名， 但值必须引用本类常量而非重复定义字符串。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see YdszMessageTopics
 */
public final class QueueChannels {

  private QueueChannels() {}

  // ==================== 消息中心（ydsz-message） ====================

  /**
   * 消息投递通道（单条消息）
   *
   * <p>消费方：{@link YdszMessageTopics#GROUP_MESSAGE}
   */
  public static final String MESSAGE_TOPIC = YdszMessageTopics.TOPIC_MESSAGE;

  /** 批量消息投递通道 */
  public static final String MESSAGE_BATCH_TOPIC = YdszMessageTopics.TOPIC_MESSAGE_BATCH;

  /** 消息死信队列通道 */
  public static final String MESSAGE_DLQ = YdszMessageTopics.DLQ_MESSAGE;

  /** 消息死信队列消费组 */
  public static final String MESSAGE_DLQ_GROUP = YdszMessageTopics.GROUP_DLQ_MESSAGE;

  // ==================== 工作流（ydsz-workflow） ====================

  /**
   * 工作流生命周期事件通道
   *
   * <p>事件类型：INSTANCE_STARTED/COMPLETED/REJECTED/TERMINATED/RECALLED,
   * TASK_CREATED/COMPLETED/URGED/TRANSFERRED/DELEGATED/TIMEOUT 等。
   */
  public static final String FLOW_EVENT = "ydsz:flow:event";

  /**
   * 流程超时事件通道
   *
   * <p>供 cronjob 模块消费，触发超时处理任务。
   */
  public static final String FLOW_TIMEOUT = "ydsz:flow:timeout";

  // ==================== 定时任务（ydsz-cronjob） ====================

  /**
   * 事件驱动调度通道
   *
   * <p>其他服务向此通道发送消息以触发定时任务执行。
   */
  public static final String JOB_EVENT_TRIGGER = "ydsz:job:event-trigger";

  /**
   * 任务执行结果通道
   *
   * <p>cronjob 模块将任务执行结果发布到此通道。
   */
  public static final String JOB_RESULT = "ydsz:job:result";

  /**
   * 任务告警事件通道
   *
   * <p>cronjob 模块将告警事件发布到此通道。
   */
  public static final String JOB_ALERT = "ydsz:job:alert";

  // ==================== Agent（ydsz-agent） ====================

  /** Agent 任务执行结果通道 */
  public static final String AGENT_TASK_RESULT = "ydsz:agent:task-result";

  /** Human-in-the-Loop 审批请求通道 */
  public static final String AGENT_APPROVAL_REQUEST = "ydsz:agent:approval-request";

  /** 知识库更新事件通道 */
  public static final String AGENT_KNOWLEDGE_UPDATE = "ydsz:agent:knowledge-update";
}
