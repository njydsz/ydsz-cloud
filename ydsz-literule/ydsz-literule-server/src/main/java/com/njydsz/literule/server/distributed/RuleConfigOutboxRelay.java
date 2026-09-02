package com.njydsz.literule.server.distributed;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.njydsz.common.event.api.DomainEvent;
import com.njydsz.common.event.model.OutboxMessage;
import com.njydsz.common.event.repository.OutboxRepository;
import com.njydsz.common.json.YdszJson;
import com.njydsz.literule.domain.event.RuleConfigRefreshEvent;
import com.njydsz.literule.server.spi.RuleConfigBroadcaster;

/**
 * 规则配置变更 Outbox 中继（P0-A1 热更新一致性）
 *
 * <p>消除"DB 已提交但分布式广播失败导致其他节点缓存陈旧"的双写不一致问题： 规则变更事务提交后，{@link
 * com.njydsz.common.event.service.OutboxService} 会以 Spring 事件形式发布 {@link OutboxMessage}，
 * 本中继在 {@code AFTER_COMMIT} 阶段捕获规则刷新消息并立即执行低延迟广播（毫秒级）， 成功后通过
 * {@link OutboxRepository#markAsSent} 标记消息已投递，避免 {@code OutboxProcessor} 重复广播。
 *
 * <p>若低延迟广播失败（Redis 短暂不可用等），消息保持 PENDING， 由 OutboxProcessor 轮询 +
 * {@link RuleConfigOutboxGateway} 以指数退避方式兜底重试， 直至投递成功或达到 maxRetries（进入死信告警）。
 *
 * <p><b>对比旧实现（RuleAdminService.publishRefreshEvent 直接 broadcast）</b>：
 *
 * <ul>
 *   <li>旧：广播失败仅 log.warn，无补偿机制，其他节点缓存永久陈旧
 *   <li>新：广播失败落 Outbox 表，可重试、可监控（OutboxHealthIndicator）、可人工补偿
 * </ul>
 *
 * @since 26.09.01
 * @author ydsz-team
 */
public class RuleConfigOutboxRelay {

  /** 日志实例 */
  private static final Logger log = LoggerFactory.getLogger(RuleConfigOutboxRelay.class);

  /** 规则配置广播器（Redis Pub/Sub） */
  private final RuleConfigBroadcaster broadcaster;

  /** Outbox 仓储（用于广播成功后标记消息为 SENT，可为 null 降级） */
  private final OutboxRepository outboxRepository;

  /** 当前节点标识（用于广播防循环） */
  private final String nodeId;

  /**
   * 构造 Outbox 中继
   *
   * @param broadcaster 规则配置广播器
   * @param outboxRepository Outbox 仓储（可为 null，为空时不做 markAsSent 去重）
   * @param nodeId 当前节点标识（host:pid）
   */
  public RuleConfigOutboxRelay(
      RuleConfigBroadcaster broadcaster, OutboxRepository outboxRepository, String nodeId) {
    this.broadcaster = broadcaster;
    this.outboxRepository = outboxRepository;
    this.nodeId = nodeId;
  }

  /**
   * 事务提交后监听 OutboxMessage，对规则刷新事件执行低延迟广播
   *
   * <p>{@code fallbackExecution=true} 确保非事务上下文（如单元测试、手动投递）下仍能触发。 广播成功标记 SENT
   * 防重复；广播失败保持 PENDING 交由 OutboxProcessor 兜底。
   *
   * @param message Outbox 消息
   */
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  @Order(200)
  public void onOutboxMessage(OutboxMessage message) {
    if (message == null || !RuleConfigRefreshEvent.EVENT_TYPE.equals(message.getEventType())) {
      return;
    }
    if (broadcaster == null || !broadcaster.isAvailable()) {
      log.warn(
          "[LiteRule-Outbox] 广播器不可用，等待 OutboxProcessor 兜底重试: id={}", message.getId());
      return;
    }
    try {
      DomainEvent domainEvent = YdszJson.fromJson(message.getPayload(), DomainEvent.class);
      if (domainEvent == null) {
        log.warn("[LiteRule-Outbox] 消息反序列化为空，跳过低延迟广播: id={}", message.getId());
        return;
      }
      RuleConfigRefreshEvent event = RuleConfigRefreshEvent.from(domainEvent);
      broadcaster.broadcast(event, nodeId);
      // 广播成功：标记为 SENT，避免 OutboxProcessor 下一轮询周期重复广播
      if (outboxRepository != null) {
        outboxRepository.markAsSent(message.getId());
        log.debug("[LiteRule-Outbox] 消息已标记 SENT: id={}", message.getId());
      }
      log.info(
          "[LiteRule-Outbox] 低延迟广播完成: id={}, ruleCode={}, changeType={}",
          message.getId(),
          event.getRuleCode(),
          event.getChangeType());
    } catch (Exception e) {
      log.warn(
          "[LiteRule-Outbox] 低延迟广播失败，由 OutboxProcessor 兜底重试: id={}, err={}",
          message.getId(),
          e.getMessage());
    }
  }
}
