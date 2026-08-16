package com.njydsz.message.server.service.impl.core;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.common.core.code.BaseResultCode;
import com.njydsz.common.core.constant.PageConstants;
import com.njydsz.common.exception.custom.SysException;
import com.njydsz.common.queue.trace.MessageTracer;
import com.njydsz.message.domain.dto.core.MessageLogQueryDTO;
import com.njydsz.message.domain.entity.core.MsgLog;
import com.njydsz.message.domain.enums.core.MessageStatusEnum;
import com.njydsz.message.domain.enums.receipt.RecallStatusEnum;
import com.njydsz.message.infra.mapper.core.MsgLogMapper;
import com.njydsz.message.server.channel.ChannelRouter;
import com.njydsz.message.server.config.MessageProperties;
import com.njydsz.message.server.config.RetryStrategyResolver;
import com.njydsz.message.server.event.DeadLetterAlertEvent;
import com.njydsz.message.server.metric.MessageMetrics;
import com.njydsz.message.server.service.core.MessageLogService;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 消息日志服务实现。
 *
 * <p>管理消息发送全链路日志 ({@code ydsz_msg_log})：发送、送达、读取、点击、退订、失败、批量 ID。
 *
 * <p>支持分页查询、按渠道/状态/时间/接收人筛选，是消息中心的「对账单」。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageLogServiceImpl implements MessageLogService {

  /** 消息日志 Mapper */
  private final MsgLogMapper msgLogMapper;

  /** 通道路由器（重发时分发） */
  private final ChannelRouter channelRouter;

  /** 重试策略解析器 */
  private final RetryStrategyResolver retryStrategyResolver;

  /** Spring 事件发布器（死信告警） */
  private final ApplicationEventPublisher eventPublisher;

  /** 消息模块配置属性 */
  private final MessageProperties messageProperties;

  /** 消息指标采集 */
  private final MessageMetrics messageMetrics;

  /** P1-4: 通道 → 上次告警时间戳(ms),用于告警冷却去重 */
  private final ConcurrentHashMap<String, Long> lastAlertTimeMap = new ConcurrentHashMap<>();

  @Override
  public MsgLog getById(String id) {
    if (!StringUtils.hasText(id)) {
      throw SysException.builder()
          .resultCode(BaseResultCode.BAD_REQUEST)
          .message("日志 ID 不能为空")
          .build();
    }
    MsgLog entity = msgLogMapper.selectById(id);
    if (entity == null) {
      throw SysException.builder()
          .resultCode(BaseResultCode.NOT_FOUND)
          .message("日志不存在: " + id)
          .build();
    }
    return entity;
  }

  @Override
  public Page<MsgLog> page(MessageLogQueryDTO query) {
    Page<MsgLog> page =
        new Page<>(
            query == null ? 1 : query.getPageNum(),
            Math.min(query == null ? 10 : query.getPageSize(), PageConstants.MAX_PAGE_SIZE));
    LambdaQueryWrapper<MsgLog> w = new LambdaQueryWrapper<>();
    if (query != null) {
      w.eq(StringUtils.hasText(query.getChannel()), MsgLog::getChannel, query.getChannel());
      w.eq(StringUtils.hasText(query.getBizType()), MsgLog::getBizType, query.getBizType());
      w.eq(StringUtils.hasText(query.getBizId()), MsgLog::getBizId, query.getBizId());
      w.eq(StringUtils.hasText(query.getStatus()), MsgLog::getStatus, query.getStatus());
      w.eq(StringUtils.hasText(query.getReceiver()), MsgLog::getReceiver, query.getReceiver());
      w.eq(StringUtils.hasText(query.getPriority()), MsgLog::getPriority, query.getPriority());
      w.eq(
          StringUtils.hasText(query.getRecallStatus()),
          MsgLog::getRecallStatus,
          query.getRecallStatus());
      w.eq(StringUtils.hasText(query.getTenantId()), MsgLog::getTenantId, query.getTenantId());
    }
    w.orderByDesc(MsgLog::getCreatedAt);
    return msgLogMapper.selectPage(page, w);
  }

  @Override
  public void markRetry(String id, LocalDateTime nextRetryAt) {
    MsgLog entity = getById(id);
    MessageStatusEnum current = parseStatus(entity.getStatus());
    if (!current.canTransitTo(MessageStatusEnum.RETRY)) {
      throw SysException.builder()
          .resultCode(BaseResultCode.BAD_REQUEST)
          .message("非法状态流转: " + current + " -> RETRY")
          .build();
    }
    entity.setStatus(MessageStatusEnum.RETRY.name());
    entity.setNextRetryAt(nextRetryAt);
    entity.setRetryCount(entity.getRetryCount() == null ? 1 : entity.getRetryCount() + 1);
    msgLogMapper.updateById(entity);
    log.info(
        "[MessageLog] 标记重试: id={} nextRetryAt={} retryCount={}",
        id,
        nextRetryAt,
        entity.getRetryCount());
  }

  @Override
  public void markDead(String id, String errorMessage) {
    MsgLog entity = getById(id);
    MessageStatusEnum current = parseStatus(entity.getStatus());
    if (!current.canTransitTo(MessageStatusEnum.DEAD)) {
      // 仅 RETRY 可流转到 DEAD；其他状态强制记录但仍校验，非法抛异常
      throw SysException.builder()
          .resultCode(BaseResultCode.BAD_REQUEST)
          .message("非法状态流转: " + current + " -> DEAD")
          .build();
    }
    entity.setStatus(MessageStatusEnum.DEAD.name());
    entity.setErrorMessage(errorMessage);
    msgLogMapper.updateById(entity);
    log.warn("[MessageLog] 标记死信: id={} err={}", id, errorMessage);
    // P1-4: 死信告警检测
    checkAndFireDeadLetterAlert(entity.getChannel());
  }

  @Override
  public void updateReceipt(String id, String receiptStatus, LocalDateTime receiptAt) {
    MsgLog entity = getById(id);
    entity.setReceiptStatus(receiptStatus);
    entity.setReceiptAt(receiptAt);
    msgLogMapper.updateById(entity);
  }

  @Override
  public void markRecalled(String id) {
    MsgLog entity = getById(id);
    MessageStatusEnum current = parseStatus(entity.getStatus());
    if (!current.canTransitTo(MessageStatusEnum.RECALLED)) {
      throw SysException.builder()
          .resultCode(BaseResultCode.BAD_REQUEST)
          .message("非法状态流转: " + current + " -> RECALLED")
          .build();
    }
    entity.setStatus(MessageStatusEnum.RECALLED.name());
    entity.setRecallStatus(RecallStatusEnum.RECALLED.name());
    entity.setRecallAt(LocalDateTime.now());
    msgLogMapper.updateById(entity);
  }

  /**
   * P1-4: 手动重发死信。
   *
   * <p>仅 DEAD 状态可重发。重置 retryCount / errorMessage / nextRetryAt， 流转为 SENDING 后立即通过 {@link
   * ChannelRouter#dispatch(MsgLog)} 重新投递。 投递失败则进入 RETRY 状态（retryCount=1）走正常重试调度，而非立即再次死信。
   */
  @Override
  public void resendDead(String logId) {
    MsgLog entity = getById(logId);
    MessageStatusEnum current = parseStatus(entity.getStatus());
    if (current != MessageStatusEnum.DEAD) {
      throw SysException.builder()
          .resultCode(BaseResultCode.BAD_REQUEST)
          .message("仅死信可手动重发,当前状态: " + current)
          .build();
    }
    try (MessageTracer.MessageTraceScope scope = MessageTracer.enter(entity.getTraceId())) {
      // 重置重试上下文
      entity.setRetryCount(0);
      entity.setErrorMessage(null);
      entity.setNextRetryAt(null);
      entity.setStatus(MessageStatusEnum.SENDING.name());
      msgLogMapper.updateById(entity);
      log.info("[MessageLog] 手动重发死信: logId={} channel={}", logId, entity.getChannel());

      long start = System.currentTimeMillis();
      try {
        String providerTraceId = channelRouter.dispatch(entity);
        long cost = System.currentTimeMillis() - start;
        entity.setStatus(MessageStatusEnum.SUCCESS.name());
        entity.setProviderTraceId(providerTraceId);
        entity.setCostMs(cost);
        msgLogMapper.updateById(entity);
        messageMetrics.recordSend(entity.getChannel(), "SUCCESS", cost);
        log.info("[MessageLog] 死信重发成功: logId={} providerTraceId={}", logId, providerTraceId);
      } catch (Exception e) {
        long cost = System.currentTimeMillis() - start;
        int newRetryCount = 1;
        entity.setRetryCount(newRetryCount);
        entity.setCostMs(cost);
        entity.setErrorMessage(e.getMessage());
        // 进入正常重试调度,而非立即再次死信
        entity.setStatus(MessageStatusEnum.RETRY.name());
        entity.setNextRetryAt(
            retryStrategyResolver.calcNextRetryAt(newRetryCount, entity.getChannel()));
        msgLogMapper.updateById(entity);
        messageMetrics.recordRetry(entity.getChannel());
        log.warn(
            "[MessageLog] 死信重发失败转重试: logId={} err={} nextRetryAt={}",
            logId,
            e.getMessage(),
            entity.getNextRetryAt());
      }
    }
  }

  /**
   * P1-4: 死信告警检测。
   *
   * <p>统计窗口内指定通道的死信数量,达到阈值且通过冷却期则发布 {@link DeadLetterAlertEvent}。 告警逻辑不抛异常,避免影响 markDead 主流程。
   *
   * @param channel 触发死信的通道
   */
  private void checkAndFireDeadLetterAlert(String channel) {
    try {
      if (!StringUtils.hasText(channel)) {
        return;
      }
      MessageProperties.DeadLetterAlertConfig cfg = messageProperties.getDeadLetterAlert();
      if (cfg == null || !cfg.isEnabled() || cfg.getThreshold() <= 0) {
        return;
      }
      // 冷却期去重:同一通道冷却期内不重复告警
      long now = System.currentTimeMillis();
      Long last = lastAlertTimeMap.get(channel);
      long cooldownMs = cfg.getCooldownMinutes() * 60_000L;
      if (last != null && (now - last) < cooldownMs) {
        return;
      }
      // 统计窗口内死信数量
      LocalDateTime windowStart = LocalDateTime.now().minusMinutes(cfg.getWindowMinutes());
      Long count =
          msgLogMapper.selectCount(
              new LambdaQueryWrapper<MsgLog>()
                  .eq(MsgLog::getStatus, MessageStatusEnum.DEAD.name())
                  .eq(MsgLog::getChannel, channel)
                  .ge(MsgLog::getCreatedAt, windowStart));
      long currentCount = count == null ? 0L : count;
      if (currentCount >= cfg.getThreshold()) {
        lastAlertTimeMap.put(channel, now);
        DeadLetterAlertEvent event =
            new DeadLetterAlertEvent(
                this, channel, currentCount, cfg.getThreshold(), cfg.getWindowMinutes());
        eventPublisher.publishEvent(event);
        log.info(
            "[MessageLog] 死信告警已触发: channel={} count={} threshold={}",
            channel,
            currentCount,
            cfg.getThreshold());
      }
    } catch (Exception e) {
      log.error("[MessageLog] 死信告警检测异常,不影响主流程: {}", e.getMessage(), e);
    }
  }

  private MessageStatusEnum parseStatus(String value) {
    try {
      return MessageStatusEnum.valueOf(value);
    } catch (Exception e) {
      throw SysException.builder()
          .resultCode(BaseResultCode.BAD_REQUEST)
          .message("非法消息状态: " + value)
          .build();
    }
  }
}
