/**
 * 消息生产层：统一封装 RocketMQ 消息发送入口，屏蔽底层 API 细节。
 *
 * <p>本包是 PMIS 消息中心的统一发送出口，所有业务模块（通知/告警/对账/审批）均通过
 * {@code RocketMQMessageProducer} 发送异步消息，避免各业务方直接依赖
 * {@code RocketMQTemplate} 带来的 API 散落与 messageId 缺失问题。
 *
 * <h3>核心组件</h3>
 * <ul>
 *   <li>{@code RocketMQMessageProducer} - 消息生产者封装组件，绑定
 *       {@code PmisMessageTopics.TOPIC_MESSAGE}，提供 {@code syncSend}（同步/可靠）与
 *       {@code asyncSend}（异步/快速）两种发送模式，自动生成雪花 {@code messageId}
 *       保证消费端幂等键可用</li>
 * </ul>
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li><b>统一入口</b>：所有发送消息的业务方法均通过本类，禁止直接注入 {@code RocketMQTemplate}</li>
 *   <li><b>messageId 自动填充</b>：若 {@code MessageRequest.messageId} 为空，自动调用
 *       {@code SnowflakeIdGenerator} 生成，保证消费端幂等键可用</li>
 *   <li><b>条件装配</b>：通过 {@code @ConditionalOnClass(RocketMQTemplate)} +
 *       {@code @ConditionalOnProperty(rocketmq.producer.enabled=true)} 实现无 MQ 环境优雅降级</li>
 *   <li><b>Topic 常量化</b>：所有 Topic 字符串集中维护在 {@code common.constant.PmisMessageTopics}，
 *       禁止字面量散落</li>
 *   <li><b>异常分类</b>：同步发送失败抛 {@code RuntimeException} 由调用方决策重试；
 *       异步发送失败仅 ERROR 日志 + 回调，不阻塞主流程</li>
 * </ul>
 *
 * <h3>使用规范</h3>
 * <ul>
 *   <li>高可靠性场景（如支付/审批通知）使用 {@code syncSend}，容忍延迟换取不丢消息</li>
 *   <li>高吞吐场景（如批量告警/心跳）使用 {@code asyncSend}，通过 {@code SendCallback} 处理结果</li>
 *   <li>禁止在事务方法内直接调用 {@code syncSend}，应使用 {@code TransactionMQProducer}
 *       实现"先发消息再提交事务"或本地消息表模式</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
package com.njydsz.pmis.system.producer;
