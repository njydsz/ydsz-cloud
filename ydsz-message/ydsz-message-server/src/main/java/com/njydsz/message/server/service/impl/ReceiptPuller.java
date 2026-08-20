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
import com.njydsz.message.domain.enums.core.MessageStatusEnum;
import com.njydsz.message.domain.enums.receipt.ReceiptStatusEnum;
import com.njydsz.message.domain.repository.MsgLogRepository;
import com.njydsz.message.domain.vo.MsgLogVO;
import com.njydsz.message.server.channel.ChannelRouter;
import com.njydsz.message.server.channel.MessageChannel;
import com.njydsz.message.server.config.MessageProperties;
import com.njydsz.message.server.service.core.MessageLogService;

/**
 * P2-9: 回执闭环调度器 —— 主动拉取回执 + 超时补偿。
 *
 * <p>对标阿里云 MessageCenter / 腾讯云 CAM 的回执闭环能力。仅依赖服务商被动回调会导致 大量消息长期停留在「回执未知」状态（{@code
 * receiptStatus=NONE}），本调度器通过两个阶段 补齐闭环：
 *
 * <ol>
 *   <li><b>主动拉取阶段</b>：扫描 {@code status=SUCCESS AND receiptStatus=NONE AND createdAt < now -
 *       pullDelayMinutes} 的消息，调用对应渠道 {@link MessageChannel#queryReceipt} 向服务商查询最新回执状态。
 *       <ul>
 *         <li>渠道支持且返回结果 → {@link MessageLogService#updateReceipt} 更新回执状态
 *         <li>渠道不支持（{@link Optional#empty()}）→ 跳过，仅等待被动回调
 *         <li>拉取异常 → 记录 WARN，不中断后续消息处理
 *       </ul>
 *   <li><b>超时补偿阶段</b>：对于 {@code createdAt < now - timeoutMinutes} 仍无回执的消息， 标记 {@code
 *       receiptStatus=TIMEOUT}，避免消息永远停留在「回执未知」状态。 超时判定优先于拉取（说明此前已尝试拉取但仍无结果）。
 * </ol>
 *
 * <p>多实例部署通过 {@link DistributedScheduled} 注解保证只有一个实例执行扫描， 锁等待 0s（非阻塞），TTL 60s，获取失败直接跳过本次扫描。
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
        Optional<com.njydsz.message.domain.dto.ReceiptResult> result = channel.queryReceipt(logVO);
        if (result.isEmpty()) {
          // 渠道不支持主动拉取，跳过等待被动回调
          skipped++;
          continue;
        }
        com.njydsz.message.domain.dto.ReceiptResult receipt = result.get();
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
