package com.njydsz.literule.server.distributed;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;

import com.njydsz.common.json.YdszJson;
import com.njydsz.common.redis.service.ops.RedisPubSubOps;
import com.njydsz.literule.domain.event.RuleConfigRefreshEvent;
import com.njydsz.literule.server.spi.RuleConfigBroadcaster;

/**
 * 基于 Redis Pub/Sub 的规则配置广播器（生产环境实现）
 *
 * <p>利用 Redis Pub/Sub 实现跨实例的规则变更事件广播，确保所有节点的规则缓存一致。
 *
 * <p>广播流程：
 *
 * <pre>
 *   节点A: RuleAdminService.save() → broadcaster.broadcast(event, sourceId)
 *                                       ↓ (Redis Pub/Sub)
 *   节点B: onMessage(event) → 校验 sourceId → publishEvent(local) → RuleHotReloader
 * </pre>
 *
 * <p>防广播风暴：消息携带 {@code sourceNodeId}，接收方忽略本节点发出的消息。
 *
 * <p>消息格式（JSON）：
 *
 * <pre>
 *   {"sourceNodeId":"hostA:1234","event":{"ruleCode":"R001","changeType":"UPDATE","operator":"admin"}}
 * </pre>
 *
 * <p><b>依赖说明</b>：使用 {@link RedisPubSubOps} 收敛所有 Redis Pub/Sub 操作，
 * 禁止直接注入 {@code RedissonClient}。豁免原因已随迁移消除（此前 RTopic 操作现由 {@link RedisPubSubOps} 替代）。
 *
 * @since 26.09.01
 * @author ydsz-team
 */
public class RedisRuleConfigBroadcaster implements RuleConfigBroadcaster {

  private static final Logger log = LoggerFactory.getLogger(RedisRuleConfigBroadcaster.class);

  /** Redis Topic 名称 */
  private static final String TOPIC_NAME = "literule:config:refresh";

  /** Redis Pub/Sub 操作组件 */
  private final RedisPubSubOps redisPubSubOps;

  /** 当前节点唯一标识（如 host:port），用于过滤本节点发出的广播消息防止广播风暴 */
  private final String selfNodeId;

  /** Spring 事件发布器，收到远端广播后转换为本地 ApplicationEvent 以驱动热加载 */
  private final ApplicationEventPublisher eventPublisher;

  /** 是否已订阅 */
  private volatile boolean subscribed = false;

  /** 订阅 ID（用于取消订阅） */
  private String subscriptionId;

  public RedisRuleConfigBroadcaster(
      RedisPubSubOps redisPubSubOps,
      String selfNodeId,
      ApplicationEventPublisher eventPublisher) {
    this.redisPubSubOps = redisPubSubOps;
    this.selfNodeId = selfNodeId;
    this.eventPublisher = eventPublisher;
  }

  @Override
  public void broadcast(RuleConfigRefreshEvent event, String sourceId) {
    if (event == null) {
      return;
    }
    try {
      BroadcastMessage message = new BroadcastMessage(sourceId, event);
      String json = YdszJson.toJson(message);
      redisPubSubOps.publish(TOPIC_NAME, json);
      log.info(
          "[Distributed-Redis] 规则变更事件已广播: ruleCode={}, changeType={}, source={}",
          event.getRuleCode(),
          event.getChangeType(),
          sourceId);
    } catch (Exception e) {
      log.warn("[Distributed-Redis] 规则变更事件广播失败: {}", e.getMessage());
    }
  }

  @Override
  public boolean isAvailable() {
    // Redis 操作组件已注入即视为可用（订阅失败不影响发布能力）
    return redisPubSubOps != null;
  }

  /**
   * 订阅 Redis Topic，接收其他节点的广播消息
   *
   * <p>收到消息后：
   *
   * <ol>
   *   <li>反序列化为 {@link BroadcastMessage}
   *   <li>校验 {@code sourceNodeId}，忽略本节点发出的消息
   *   <li>通过 {@link ApplicationEventPublisher} 在本地发布 {@link RuleConfigRefreshEvent}
   * </ol>
   */
  public void subscribe() {
    if (subscribed) {
      return;
    }
    try {
      subscriptionId = redisPubSubOps.subscribe(TOPIC_NAME, this::handleReceivedMessage);
      subscribed = true;
      log.info("[Distributed-Redis] 已订阅规则变更广播 Topic: {}", TOPIC_NAME);
    } catch (Exception e) {
      log.warn("[Distributed-Redis] 订阅广播 Topic 失败: {}", e.getMessage());
    }
  }

  /** 处理接收到的广播消息 */
  // YDIZ-WARN-001 允许保留：Redis 反序列化配置快照，由 DTO 反序列化后强校验
  @SuppressWarnings("unchecked")
  private void handleReceivedMessage(RedisPubSubOps.PubSubMessage message) {
    if (message == null || message.getBody() == null) {
      return;
    }
    try {
      String payload = message.getBody(String.class);
      if (payload == null || payload.isEmpty()) {
        return;
      }
      BroadcastMessage broadcastMsg = YdszJson.fromJson(payload, BroadcastMessage.class);
      if (broadcastMsg == null || broadcastMsg.getEvent() == null) {
        return;
      }
      // 忽略本节点发出的消息，防止循环
      if (selfNodeId.equals(broadcastMsg.getSourceNodeId())) {
        return;
      }
      log.info(
          "[Distributed-Redis] 收到规则变更广播: ruleCode={}, changeType={}, source={}",
          broadcastMsg.getEvent().getRuleCode(),
          broadcastMsg.getEvent().getChangeType(),
          broadcastMsg.getSourceNodeId());
      // 在本地发布事件，触发 RuleHotReloader 热加载
      if (eventPublisher != null) {
        eventPublisher.publishEvent(broadcastMsg.getEvent());
      }
    } catch (Exception e) {
      log.warn("[Distributed-Redis] 广播消息处理失败: {}", e.getMessage());
    }
  }

  /** 广播消息包装（携带 sourceNodeId 用于接收方忽略自身消息） */
  public static class BroadcastMessage {
    /** 发送节点 ID */
    private String sourceNodeId;

    /** 规则变更事件 */
    private RuleConfigRefreshEvent event;

    public BroadcastMessage() {}

    public BroadcastMessage(String sourceNodeId, RuleConfigRefreshEvent event) {
      this.sourceNodeId = sourceNodeId;
      this.event = event;
    }

    public String getSourceNodeId() {
      return sourceNodeId;
    }

    public void setSourceNodeId(String sourceNodeId) {
      this.sourceNodeId = sourceNodeId;
    }

    public RuleConfigRefreshEvent getEvent() {
      return event;
    }

    public void setEvent(RuleConfigRefreshEvent event) {
      this.event = event;
    }
  }
}
