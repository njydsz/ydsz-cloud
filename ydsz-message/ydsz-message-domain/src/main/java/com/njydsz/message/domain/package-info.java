/**
 * 消息领域层，包含消息仓储接口、DTO、值对象、枚举等.
 *
 * <p>本模块定义了消息子系统的核心领域模型与仓储接口契约，覆盖消息模板、消息日志、批量任务、
 * 回执记录、用户偏好、订阅管理、渠道绑定、聚合统计、离线消息等业务子域。通过严格的领域边界
 * 划分，确保各子域的仓储接口与数据模型独立演化、互不耦合。</p>
 *
 * <p>领域模型主要构成：</p>
 * <ul>
 *   <li>仓储接口：{@code MsgTemplateRepository}、{@code MsgLogRepository}、{@code OutboxEventRepository}、
 *       {@code MsgBatchRepository}、{@code MsgReceiptRepository}、{@code MsgPreferenceRepository}、
 *       {@code MsgSubscriptionRepository}、{@code MsgUserChannelRepository} 等，覆盖消息全生命周期</li>
 *   <li>DTO 与值对象：{@code MessageSendDTO}、{@code ReceiptResult}、{@code ReceiptCallbackDTO}、
 *       {@code BatchSendRequestDTO} 等，定义跨层数据传输契约</li>
 *   <li>枚举：{@code MessageChannelEnum}、{@code MessageStatusEnum}、{@code MessagePriorityEnum}、
 *       {@code SendStrategyEnum}、{@code ReceiptStatusEnum}、{@code TemplateStatusEnum} 等，
 *       定义状态、通道、策略等维度</li>
 *   <li>领域事件：{@code MessageSentEvent}、{@code MessageScheduledEvent}、{@code MessageRecalledEvent}、
 *       {@code BatchCompletedEvent}、{@code OutboxEvent} 等，驱动异步集成与 Outbox 模式</li>
 * </ul>
 *
 * <h3>子域划分</h3>
 *
 * <ul>
 *   <li>模板子域 -- 模板 CRUD、版本管理与审核</li>
 *   <li>发送子域 -- 发送日志、渠道绑定与变量源</li>
 *   <li>回执子域 -- 回执回调、状态流转与统计</li>
 *   <li>偏好子域 -- 用户通知偏好、订阅与退订管理</li>
 *   <li>聚合子域 -- 数据聚合、漏斗统计与成本分析</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
package com.njydsz.message.domain;
