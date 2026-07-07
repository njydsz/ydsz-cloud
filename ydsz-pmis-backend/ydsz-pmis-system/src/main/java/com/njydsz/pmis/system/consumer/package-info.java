/**
 * 消息消费层：负责 RocketMQ 业务消息与死信消息的异步消费处理。
 *
 * <p>本包实现 PMIS 消息中心的消息订阅端，是 {@code producer} 生产消息的下游消费者。
 * 通过 {@code @RocketMQMessageListener} 监听指定 Topic，结合 Redis 实现消费幂等，
 * 并通过 DLQ 兜底机制保证消息不丢失。
 *
 * <h3>核心组件</h3>
 * <ul>
 *   <li>{@code MessageConsumer} - 业务消息消费者，监听 {@code pmis-message-topic}，
 *       支持 Redis SET NX EX 幂等防重、BizException 落库、异常重投</li>
 *   <li>{@code MessageDlqConsumer} - 死信队列消费者，监听 {@code %DLQ%pmis-message-consumer}，
 *       负责将重试耗尽的消息落库到 {@code pmis_message_log}（status=DEAD）并告警</li>
 * </ul>
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li><b>幂等优先</b>：基于 {@code messageId}（雪花算法）或 {@code bizType:bizId:templateCode:receiver}
 *       组合键构建幂等键，TTL=10 分钟覆盖 RocketMQ 全部重投窗口</li>
 *   <li><b>异常分级</b>：{@code BizException} 保留锁不重试（业务问题重投无意义），
 *       系统异常释放锁触发 RocketMQ 重投（瞬时故障可自愈）</li>
 *   <li><b>不静默失败</b>：原 BizException 静默丢弃已改造为落库 {@code pmis_message_log}，
 *       保证可观测可补偿</li>
 *   <li><b>条件装配</b>：通过 {@code @ConditionalOnClass} + {@code @ConditionalOnProperty}
 *       实现 RocketMQ 不存在时优雅降级</li>
 * </ul>
 *
 * <h3>使用规范</h3>
 * <ul>
 *   <li>新消费者需显式声明 {@code maxReconsumeTimes}，超过上限自动转 DLQ</li>
 *   <li>消费逻辑禁止阻塞超过 {@code rocketmq.consumer.consumeTimeout}（默认 15 分钟）</li>
 *   <li>幂等键构造须使用业务可重建字段，避免使用时间戳等不可重建信息</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
package com.njydsz.pmis.system.consumer;
