package com.njydsz.message.server.service.impl.receipt;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.njydsz.common.lock.annotation.DistributedScheduled;
import com.njydsz.message.domain.constant.MessageConstants;
import com.njydsz.message.domain.dto.MessageLogQueryDTO;
import com.njydsz.message.domain.dto.ReceiptResultDTO;
import com.njydsz.message.domain.enums.core.MessageStatusEnum;
import com.njydsz.message.domain.enums.receipt.ReceiptStatusEnum;
import com.njydsz.message.domain.repository.MsgLogRepository;
import com.njydsz.message.domain.vo.MsgLogVO;
import com.njydsz.message.server.channel.ChannelRouter;
import com.njydsz.message.server.channel.MessageChannel;
import com.njydsz.message.server.config.MessageProperties;
import com.njydsz.message.server.service.core.MessageLogService;

/**
 * 回执闭环调度器，通过主动拉取和超时补偿两阶段补齐消息回执闭环。
 *
 * <p>阶段一（主动拉取）：扫描 status=SUCCESS AND receiptStatus=NONE AND createdAt<pullDelayMinutes 的消息，
 * 调用对应渠道 MessageChannel.queryReceipt 向服务商查询最新回执状态；
 * 阶段二（超时补偿）：对 createdAt<timeoutMinutes 仍无回执的消息标记 receiptStatus=TIMEOUT。
 * 多实例部署通过 DistributedScheduled 分布式锁保证单实例执行扫描（非阻塞，TTL 60s）。
 *
 * @author ydsz
 * @since 26.09.24
 */
@Slf4j
@Component
@RequiredArgsConstructor
@EnableScheduling
@ConditionalOnProperty(
    prefix = "ydsz.message",
    name = "receipt-pull-enabled",
    havingValue = "true",
    matchIfMissing = true)
public class ReceiptPuller {

  private final MsgLogRepository msgLogRepository;
  private final ChannelRouter channelRouter;
  private final MessageLogService messageLogService;
  private final MessageProperties messageProperties;

  /**
   * 定时扫描回执缺失的消息。
   *
   * <p>默认 120s 扫描一次，通过 {@code ydsz.message.receipt-pull-scan-interval-ms} 配置。 分布式锁通过 {@link
   * DistributedScheduled} 注解自动管理，TTL 60s，获取失败直接跳过。
   */
  @Scheduled(fixedDelayString = "${ydsz.message.receipt-pull-scan-interval-ms:120000}")
  @DistributedScheduled(lockKey = "message:receipt-pull", leaseTime = 60)
  public void scan() {
    try {
      doScan();
    } catch (Exception e) {
      log.error("[ReceiptPuller] 扫描异常: {}", e.getMessage(), e);
    }
  }

  /** 执行回执拉取与超时补偿扫描。 */
  private void doScan() {
    LocalDateTime now = LocalDateTime.now();
    // 拉取阈值：发送成功后 pullDelayMinutes 分钟才开始主动拉取（给服务商回调留窗口）
    LocalDateTime pullThreshold = now.minusMinutes(messageProperties.getReceiptPullDelayMinutes());
    // 超时阈值：超过 timeoutMinutes 仍无回执则标记 TIMEOUT
    LocalDateTime timeoutThreshold = now.minusMinutes(messageProperties.getReceiptTimeoutMinutes());

    // ① 先批量处理超时消息:createdAt < timeoutThreshold → 标记 TIMEOUT
    MessageLogQueryDTO timeoutQuery = new MessageLogQueryDTO();
    timeoutQuery.setStatus(MessageStatusEnum.SUCCESS.name());
    timeoutQuery.setReceiptStatus(ReceiptStatusEnum.NONE.name());
    timeoutQuery.setEndTime(timeoutThreshold.toString());
    timeoutQuery.setPageNum(1);
    timeoutQuery.setPageSize(MessageConstants.RECEIPT_PULL_BATCH_SIZE);
    List<MsgLogVO> timeoutMsgs = msgLogRepository.findList(timeoutQuery);
    int timeout = 0;
    for (MsgLogVO logVO : timeoutMsgs) {
      try {
        messageLogService.updateReceipt(
            logVO.getId(), ReceiptStatusEnum.TIMEOUT.name(), LocalDateTime.now());
        timeout++;
      } catch (Exception e) {
        log.warn("[ReceiptPuller] 标记超时异常: logId={} err={}", logVO.getId(), e.getMessage());
      }
    }

    // ② 查询待主动拉取的消息:timeoutThreshold <= createdAt < pullThreshold
    MessageLogQueryDTO pendingQuery = new MessageLogQueryDTO();
    pendingQuery.setStatus(MessageStatusEnum.SUCCESS.name());
    pendingQuery.setReceiptStatus(ReceiptStatusEnum.NONE.name());
    pendingQuery.setStartTime(timeoutThreshold.toString());
    pendingQuery.setEndTime(pullThreshold.toString());
    pendingQuery.setPageNum(1);
    pendingQuery.setPageSize(MessageConstants.RECEIPT_PULL_BATCH_SIZE);
    List<MsgLogVO> pending = msgLogRepository.findList(pendingQuery);
    if (pending.isEmpty() && timeoutMsgs.isEmpty()) {
      return;
    }
    log.info("[ReceiptPuller] 待处理回执: 主动拉取 {} 条, 超时标记 {} 条", pending.size(), timeoutMsgs.size());

    int pulled = 0;
    int updated = 0;
    int skipped = 0;
    for (MsgLogVO logVO : pending) {
      try {
        pulled++;
        // 主动拉取：调用渠道 queryReceipt
        MessageChannel channel = channelRouter.route(logVO.getChannel());
        Optional<ReceiptResultDTO> result = channel.queryReceipt(logVO);
        if (result.isEmpty()) {
          // 渠道不支持主动拉取，跳过等待被动回调
          skipped++;
          continue;
        }
        ReceiptResultDTO receipt = result.get();
        messageLogService.updateReceipt(
            logVO.getId(), receipt.getStatus().name(), LocalDateTime.now());
        updated++;
      } catch (Exception e) {
        log.warn("[ReceiptPuller] 拉取回执异常: logId={} err={}", logVO.getId(), e.getMessage());
        skipped++;
      }
    }
    log.info(
        "[ReceiptPuller] 扫描完成: pulled={} updated={} timeout={} skipped={}",
        pulled,
        updated,
        timeout,
        skipped);
  }
}
