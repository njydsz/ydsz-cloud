package com.njydsz.message.server.service.core;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.njydsz.message.domain.event.OutboxEvent;
import com.njydsz.message.domain.repository.MsgLogRepository;
import com.njydsz.message.domain.repository.OutboxEventRepository;
import com.njydsz.message.domain.vo.MsgLogVO;

/**
 * 消息发送事务包装服务。
 *
 * <p>解决 Spring 同类 self-invocation 事务不生效问题：将落库 PENDING + 写 Outbox 封装在独立 Bean 的
 * {@code @Transactional} 方法中, 确保 OutboxDomainEventPublisher 能感知事务上下文, 真正实现 Outbox 模式的异步发布。
 *
 * <p><b>设计定位：</b>仅承担事务包装职责, 被 {@link
 * com.njydsz.message.server.service.impl.MessageServiceImpl} 调用。
 *
 * <p><b>事务传播：</b>{@link Propagation#REQUIRED} — 有事务则加入, 无事务则新建。保证 msgLog 落库与 Outbox 写入在同一事务中,
 * 避免 msgLog 已落库但 Outbox 写入失败的不一致场景。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see com.njydsz.message.server.event.OutboxDomainEventPublisher
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageSendTxService {

  private final MsgLogRepository msgLogRepository;
  private final OutboxEventRepository outboxEventRepository;
  private final MessageTraceService messageTraceService;

  /**
   * 同步发送的事务包装：落库 PENDING + 写 Outbox 在同一事务中。
   *
   * <p>确保 {@link com.njydsz.message.server.event.OutboxDomainEventPublisher} 因存在事务上下文而使用
   * {@link org.springframework.transaction.support.TransactionSynchronization#afterCommit()} 注册 Outbox 写入,
   * 而非回退到同步发布。
   *
   * <p>同时记录轨迹节点 {@code "PERSISTED"}, 与 {@code MessageServiceImpl} 中其他 trace 节点保持一致。
   *
   * @param logVO 消息日志 VO（已构造，未落库）
   * @param outboxEvent Outbox 事件（可为 null，为 null 时仅落库 msgLog）
   */
  @Transactional(propagation = Propagation.REQUIRED)
  public void insertLogAndOutbox(MsgLogVO logVO, OutboxEvent outboxEvent) {
    msgLogRepository.save(logVO);
    if (outboxEvent != null) {
      outboxEventRepository.save(outboxEvent);
    }
    messageTraceService.recordTrace(
        logVO.getMsgId(),
        "PERSISTED",
        "SUCCESS",
        logVO.getChannel(),
        "PENDING_CREATED");
  }
}
