package com.njydsz.literule.server.distributed;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.common.event.api.DomainEvent;
import com.njydsz.common.event.gateway.EventPublishGateway;
import com.njydsz.common.event.model.OutboxMessage;
import com.njydsz.common.json.YdszJson;
import com.njydsz.literule.domain.event.RuleConfigRefreshEvent;
import com.njydsz.literule.server.spi.RuleConfigBroadcaster;

/**
 * 规则配置变更 Outbox 投递网关（P0-A1 热更新一致性）
 *
 * <p>实现 {@link EventPublishGateway}，将 Outbox 表中 PENDING 的规则配置变更消息 投递到 Redis Pub/Sub（复用
 * {@link RuleConfigBroadcaster}），作为 {@link RuleConfigOutboxRelay} 低延迟广播失败后的可靠兜底路径：
 *
 * <pre>
 *   RuleAdminService.save()（事务内写 Outbox 表）
 *       ├─ afterCommit → RuleConfigOutboxRelay（低延迟广播，成功即 markAsSent）
 *       └─ OutboxProcessor 轮询 PENDING → RuleConfigOutboxGateway（失败重试兜底）
 * </pre>
 *
 * <p>职责边界：
 *
 * <ul>
 *   <li>仅处理 {@link RuleConfigRefreshEvent#EVENT_TYPE} 规则刷新事件
 *   <li>非规则刷新事件返回 false，沿用 Noop 网关语义（进入重试/死信流程，避免静默丢失）
 *   <li>广播失败返回 false，触发 Outbox 指数退避重试，直至达到 maxRetries
 * </ul>
 *
 * @since 1.0.0
 * @author ydsz-team
 */
public class RuleConfigOutboxGateway implements EventPublishGateway {

  /** 日志实例 */
  private static final Logger LOG = LoggerFactory.getLogger(RuleConfigOutboxGateway.class);

  /** 规则配置广播器（Redis Pub/Sub） */
  private final RuleConfigBroadcaster broadcaster;

  /** 当前节点标识（用于广播防循环，与 RedisRuleConfigBroadcaster 的 selfNodeId 一致） */
  private final String nodeId;

  /**
   * 构造 Outbox 投递网关
   *
   * @param broadcaster 规则配置广播器
   * @param nodeId 当前节点标识（host:pid）
   */
  public RuleConfigOutboxGateway(RuleConfigBroadcaster broadcaster, String nodeId) {
    this.broadcaster = broadcaster;
    this.nodeId = nodeId;
  }

  /**
   * 投递 Outbox 消息到 Redis Pub/Sub
   *
   * @param message Outbox 消息
   * @return true=投递成功或无需处理；false=投递失败（触发重试）
   */
  @Override
  public boolean publish(OutboxMessage message) {
    if (message == null) {
      return true;
    }
    // 仅处理规则配置刷新事件；其他事件交还重试/死信流程（与 Noop 语义一致）
    if (!RuleConfigRefreshEvent.EVENT_TYPE.equals(message.getEventType())) {
      return false;
    }
    if (broadcaster == null || !broadcaster.isAvailable()) {
      LOG.warn(
          "[LiteRule-Outbox] 广播器不可用，Outbox 消息进入重试: id={}", message.getId());
      return false;
    }
    try {
      DomainEvent domainEvent = YdszJson.fromJson(message.getPayload(), DomainEvent.class);
      if (domainEvent == null) {
        LOG.warn("[LiteRule-Outbox] 消息反序列化为空，跳过: id={}", message.getId());
        return true;
      }
      RuleConfigRefreshEvent event = RuleConfigRefreshEvent.from(domainEvent);
      broadcaster.broadcast(event, nodeId);
      LOG.info(
          "[LiteRule-Outbox] Outbox 消息已投递: id={}, ruleCode={}, changeType={}",
          message.getId(),
          event.getRuleCode(),
          event.getChangeType());
      return true;
    } catch (Exception e) {
      LOG.warn(
          "[LiteRule-Outbox] Outbox 消息投递失败: id={}, err={}", message.getId(), e.getMessage());
      return false;
    }
  }
}
