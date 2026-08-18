package com.njydsz.message.server.service.impl;

import java.time.LocalDateTime;
import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.njydsz.common.lock.annotation.DistributedScheduled;
import com.njydsz.common.queue.trace.MessageTracer;
import com.njydsz.message.domain.entity.core.MsgLog;
import com.njydsz.message.domain.enums.core.MessageStatusEnum;
import com.njydsz.message.infra.repository.MsgLogRepository;
import com.njydsz.message.server.channel.ChannelRouter;
import com.njydsz.message.server.metric.MessageMetrics;

/**
 * P0-3: 定时消息调度扫描器。
 *
 * <p>定时扫描 {@code status=SCHEDULED AND scheduled_at <= now} 的消息，在分布式锁内 逐条触发发送：
 *
 * <ul>
 *   <li>成功 → SUCCESS
 *   <li>失败 → 走重试流程（RETRY + 指数退避）
 * </ul>
 *
 * <p>多实例部署通过 Redisson 分布式锁保证只有一个实例执行扫描。 默认 30s 扫描一次，可通过 {@code
 * ydsz.message.scheduled-scan-interval-ms} 配置。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
@EnableScheduling
@ConditionalOnProperty(
    prefix = "ydsz.message",
    name = "scheduled-enabled",
    havingValue = "true",
    matchIfMissing = true)
public class ScheduledMessageScanner {

  private final MsgLogRepository msgLogRepository;
  private final ChannelRouter channelRouter;
  private final MessageMetrics messageMetrics;

  /** 单次扫描批量大小 */
  private static final int BATCH_SIZE = 200;

  /**
   * 定时扫描到期消息。
   *
   * <p>默认 30s 扫描一次，分布式锁通过 {@link DistributedScheduled} 注解自动管理，TTL 60s，获取失败直接跳过。
   */
  @Scheduled(fixedDelayString = "${ydsz.message.scheduled-scan-interval-ms:30000}")
  @DistributedScheduled(lockKey = "message:scheduled-scan", leaseTime = 60)
  public void scan() {
    try {
      doScan();
    } catch (Exception e) {
      log.error("[ScheduledScanner] 扫描异常: {}", e.getMessage(), e);
    }
  }

  /** 执行定时消息扫描。 */
  private void doScan() {
    LocalDateTime now = LocalDateTime.now();
    List<MsgLog> due =
        msgLogRepository.selectList(
            new LambdaQueryWrapper<MsgLog>()
                .eq(MsgLog::getStatus, MessageStatusEnum.SCHEDULED.name())
                .le(MsgLog::getScheduledAt, now)
                .last("LIMIT " + BATCH_SIZE));
    if (due.isEmpty()) {
      return;
    }
    log.info("[ScheduledScanner] 到期定时消息 {} 条", due.size());
    int success = 0;
    int failed = 0;
    for (MsgLog logDO : due) {
      try {
        sendScheduledMessage(logDO);
        success++;
      } catch (Exception e) {
        log.error("[ScheduledScanner] 定时消息发送异常: logId={} err={}", logDO.getId(), e.getMessage());
        failed++;
      }
    }
    log.info("[ScheduledScanner] 扫描完成: total={} success={} failed={}", due.size(), success, failed);
  }

  /**
   * 发送单条定时消息：状态流转 SCHEDULED → SENDING → dispatch → SUCCESS/RETRY。
   *
   * @param logDO 消息日志实体
   */
  private void sendScheduledMessage(MsgLog logDO) {
    try (MessageTracer.MessageTraceScope scope = MessageTracer.enter(logDO.getTraceId())) {
      logDO.setStatus(MessageStatusEnum.SENDING.name());
      msgLogRepository.updateById(logDO);
      long start = System.currentTimeMillis();
      try {
        String providerTraceId = channelRouter.dispatch(logDO);
        long cost = System.currentTimeMillis() - start;
        logDO.setStatus(MessageStatusEnum.SUCCESS.name());
        logDO.setProviderTraceId(providerTraceId);
        logDO.setCostMs(cost);
        msgLogRepository.updateById(logDO);
        messageMetrics.recordSend(logDO.getChannel(), "SUCCESS", cost);
        log.info(
            "[ScheduledScanner] 定时消息发送成功: msgId={} scheduledAt={} cost={}ms",
            logDO.getMsgId(),
            logDO.getScheduledAt(),
            cost);
      } catch (Exception e) {
        long cost = System.currentTimeMillis() - start;
        logDO.setCostMs(cost);
        logDO.setErrorMessage(e.getMessage());
        logDO.setStatus(MessageStatusEnum.RETRY.name());
        logDO.setRetryCount(1);
        logDO.setNextRetryAt(LocalDateTime.now().plusSeconds(30));
        msgLogRepository.updateById(logDO);
        messageMetrics.recordRetry(logDO.getChannel());
        log.warn(
            "[ScheduledScanner] 定时消息发送失败转重试: msgId={} err={}", logDO.getMsgId(), e.getMessage());
      }
    }
  }
}
