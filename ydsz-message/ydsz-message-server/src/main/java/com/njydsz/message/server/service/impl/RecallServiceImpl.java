package com.njydsz.message.server.service.impl.receipt;

import java.time.Duration;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.njydsz.common.core.code.YdszResultCode;
import com.njydsz.common.exception.custom.SysException;
import com.njydsz.message.domain.enums.core.MessageStatusEnum;
import com.njydsz.message.domain.enums.receipt.RecallStatusEnum;
import com.njydsz.message.domain.event.MessageRecalledEvent;
import com.njydsz.message.domain.repository.MsgLogRepository;
import com.njydsz.message.domain.repository.MsgNotificationRepository;
import com.njydsz.message.domain.vo.MsgLogVO;
import com.njydsz.message.domain.vo.MsgNotificationVO;
import com.njydsz.message.infra.entity.MsgTrace;
import com.njydsz.message.server.channel.recall.RecallChannel;
import com.njydsz.message.server.channel.recall.RecallChannelRouter;
import com.njydsz.message.server.event.DomainEventPublisher;
import com.njydsz.message.server.realtime.RealtimePushService;
import com.njydsz.message.server.service.core.MessageLogService;
import com.njydsz.message.server.service.core.MessageTraceService;
import com.njydsz.message.server.service.impl.MessageRecallPushService;
import com.njydsz.message.server.service.receipt.RecallService;

/**
 * 消息撤回服务实现。
 *
 * <p>实现已发送消息的撤回/作废：短信可联系运营商撤回（窗口期内）、IM 渠道调用回撤 API、
 *
 * <p>邮件/站内信标记为 RECALLED。撤回记录写入 {@code ydsz_msg_log} 状态变更轨迹。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecallServiceImpl implements RecallService {

  /** 站内通知 Repository */
  private final MsgNotificationRepository msgNotificationRepository;

  /** 消息日志 Repository */
  private final MsgLogRepository msgLogRepository;

  /** 实时推送服务（撤回通知推送） */
  private final RealtimePushService realtimePushService;

  /** P2-19: 消息撤回实时推送（携带撤回原因/时间戳等元数据） */
  private final MessageRecallPushService messageRecallPushService;

  /** 消息日志服务（撤回状态更新） */
  private final MessageLogService messageLogService;

  /** 消息全链路追踪服务 */
  private final MessageTraceService messageTraceService;

  /** P2-F2: 撤回通道路由器 */
  private final RecallChannelRouter recallChannelRouter;

  /** P2-A4: 领域事件发布器 */
  private final DomainEventPublisher domainEventPublisher;

  /**
   * 撤回单条站内通知。
   *
   * <p>校验通知存在且属于当前用户（越权抛 FORBIDDEN），置 {@code recallStatus=RECALLED} 并记录撤回时间，
   * 推送撤回事件到前端。仅本人可撤回，保证数据隔离。
   *
   * @param userId 用户 ID（须为通知接收人）
   * @param notificationId 通知 ID
   * @return true 表示撤回成功
   * @throws com.njydsz.common.exception.custom.SysException 参数为空 / 通知不存在 / 越权时
   */
  @Override
  @Transactional(rollbackFor = Exception.class)
  public boolean recallNotification(String userId, String notificationId) {
    if (!StringUtils.hasText(userId) || !StringUtils.hasText(notificationId)) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("用户 ID 与通知 ID 不能为空")
          .build();
    }
    MsgNotificationVO n = msgNotificationRepository.selectById(notificationId);
    if (n == null) {
      throw SysException.builder()
          .resultCode(YdszResultCode.NOT_FOUND)
          .message("通知不存在: " + notificationId)
          .build();
    }
    if (!userId.equals(n.getReceiverId())) {
      throw SysException.builder()
          .resultCode(YdszResultCode.FORBIDDEN)
          .message("仅可撤回本人的通知")
          .build();
    }
    n.setRecallStatus(RecallStatusEnum.RECALLED.name());
    n.setRecallAt(LocalDateTime.now());
    msgNotificationRepository.updateById(n);
    // P2-19: 推送撤回事件到前端（携带撤回原因/时间戳）
    messageRecallPushService.pushRecall(userId, notificationId, "通知撤回");
    log.info("[Recall] 撤回通知: id={} user={}", notificationId, userId);
    return true;
  }

  /**
   * 按消息日志 ID 撤回已发送消息。
   *
   * <p>标记消息为已撤回，并通过 WebSocket 向接收人推送撤回事件、记录全链路撤回轨迹； 仅当消息存在且接收人有效时推送。logId 为空抛 BAD_REQUEST。
   *
   * @param logId 消息日志 ID
   * @return true 表示撤回成功
   * @throws com.njydsz.common.exception.custom.SysException logId 为空时
   */
  @Override
  public boolean recallMessage(String logId) {
    if (!StringUtils.hasText(logId)) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("日志 ID 不能为空")
          .build();
    }
    messageLogService.markRecalled(logId);
    // P0-4: 查找消息并通过 WebSocket 推送撤回事件
    MsgLogVO logDO = msgLogRepository.selectById(logId);
    if (logDO != null && StringUtils.hasText(logDO.getReceiver())) {
      // P2-19: 推送撤回事件（携带消息 ID/撤回原因/时间戳）
      messageRecallPushService.pushRecall(logDO.getReceiver(), logDO.getMsgId(), "消息撤回");
      // P0-2: 记录撤回轨迹
      messageTraceService.recordTrace(
          logDO.getMsgId(),
          MsgTrace.Node.RECALLED,
          "SUCCESS",
          logDO.getChannel(),
          "消息已撤回: logId=" + logId);
    }
    log.info("[Recall] 撤回消息: logId={}", logId);
    return true;
  }

  /**
   * P0-4: 按 msgId 撤回已发送消息。
   * 
   * <p>校验撤回时间窗口（默认 30 分钟），超时不可撤回。 撤回后更新状态为 RECALLED 并推送前端撤回事件。
   *
   * @param msgId 参数说明
   * @return 返回值说明
   */
  @Override
  @Transactional(rollbackFor = Exception.class)
  public boolean recallByMsgId(String msgId) {
    if (!StringUtils.hasText(msgId)) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("消息 ID 不能为空")
          .build();
    }
    // 按 msgId 查询消息日志
    MsgLogVO logDO =
        msgLogRepository.selectOne(
            new LambdaQueryWrapper<MsgLogVO>().eq(MsgLog::getMsgId, msgId).last("LIMIT 1"));
    if (logDO == null) {
      throw SysException.builder()
          .resultCode(YdszResultCode.NOT_FOUND)
          .message("消息不存在: msgId=" + msgId)
          .build();
    }
    // P2-B5: 终态消息（DEAD/SKIPPED）不可撤回
    if (logDO.getStatus() == MessageStatusEnum.DEAD || logDO.getStatus() == MessageStatusEnum.SKIPPED) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("消息状态为 " + logDO.getStatus().name() + "，不可撤回")
          .build();
    }
    // 校验撤回时间窗口（当前实体无 sentAt 字段，以 createdAt 为基准）
    if (logDO.getCreatedAt() != null) {
      long minutesElapsed = Duration.between(logDO.getCreatedAt(), LocalDateTime.now()).toMinutes();
      if (minutesElapsed > RECALL_WINDOW_MINUTES) {
        throw SysException.builder()
            .resultCode(YdszResultCode.BAD_REQUEST)
            .message("消息发送已超过 " + RECALL_WINDOW_MINUTES + " 分钟，不可撤回")
            .build();
      }
    }
    // P2-B5: 已撤回则幂等返回 false，不抛异常
    if (RecallStatusEnum.RECALLED.name().equals(logDO.getRecallStatus())) {
      log.info("[Recall] 消息已撤回，跳过重复撤回: msgId={}", msgId);
      return false;
    }
    // 执行撤回
    logDO.setRecallStatus(RecallStatusEnum.RECALLED.name());
    logDO.setRecallAt(LocalDateTime.now());
    msgLogRepository.updateById(logDO);

    // P2-F2: 路由到通道对应的撤回实现
    RecallChannel.RecallResult recallResult = recallChannelRouter.routeAndRecall(logDO);

    // P2-19: 推送撤回事件到前端（携带撤回原因/时间戳）
    if (StringUtils.hasText(logDO.getReceiver())) {
      messageRecallPushService.pushRecall(logDO.getReceiver(), msgId, "消息撤回");
    }
    // P0-2: 记录撤回轨迹
    messageTraceService.recordTrace(
        msgId,
        MsgTraceDO.Node.RECALLED,
        "SUCCESS",
        logDO.getChannel(),
        "消息已撤回: msgId=" + msgId + ", platformRecall=" + recallResult.platformRecallSucceeded());
    // P2-A4: 发布消息撤回领域事件
    domainEventPublisher.publish(
        new MessageRecalledEvent(
            logDO.getTenantId(), msgId, logDO.getChannel(), true, recallResult.failureReason()));
    log.info(
        "[Recall] 按 msgId 撤回成功: msgId={} channel={} platformRecall={}",
        msgId,
        logDO.getChannel(),
        recallResult.platformRecallSucceeded());
    return true;
  }

  /**
   * 按业务类型+单据 ID 批量撤回消息与通知。
   *
   * <p>将对应 {@code bizType/bizId} 下处于 NONE 状态的通知与消息日志统一置 RECALLED（仅更新非终态，幂等）， 返回两类受影响记录数之和。
   *
   * @param bizType 业务类型
   * @param bizId 单据 ID
   * @return 实际撤回的通知数 + 消息数
   * @throws com.njydsz.common.exception.custom.SysException bizType 或 bizId 为空时
   */
  @Override
  @Transactional(rollbackFor = Exception.class)
  public int recallBatch(String bizType, String bizId) {
    if (!StringUtils.hasText(bizType) || !StringUtils.hasText(bizId)) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("业务类型与单据 ID 不能为空")
          .build();
    }
    // 通知批量撤回
    int notifCount =
        msgNotificationRepository.update(
            null,
            new LambdaUpdateWrapper<MsgNotificationVO>()
                .eq(MsgNotification::getBizType, bizType)
                .eq(MsgNotification::getBizId, bizId)
                .eq(MsgNotification::getRecallStatus, RecallStatusEnum.NONE.name())
                .set(MsgNotification::getRecallStatus, RecallStatusEnum.RECALLED.name())
                .set(MsgNotification::getRecallAt, LocalDateTime.now()));
    // 消息日志批量撤回（仅更新非终态）
    int logCount =
        msgLogRepository.update(
            null,
            new LambdaUpdateWrapper<MsgLogVO>()
                .eq(MsgLog::getBizType, bizType)
                .eq(MsgLog::getBizId, bizId)
                .eq(MsgLog::getRecallStatus, RecallStatusEnum.NONE.name())
                .set(MsgLog::getRecallStatus, RecallStatusEnum.RECALLED.name())
                .set(MsgLog::getRecallAt, LocalDateTime.now()));
    log.info(
        "[Recall] 批量撤回: bizType={} bizId={} notif={} log={}", bizType, bizId, notifCount, logCount);
    return notifCount + logCount;
  }
}
