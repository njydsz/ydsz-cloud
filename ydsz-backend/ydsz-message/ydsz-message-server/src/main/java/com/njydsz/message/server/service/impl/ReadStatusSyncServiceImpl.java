package com.njydsz.message.server.service.impl.receipt;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.njydsz.common.core.response.BaseResultCode;
import com.njydsz.common.exception.custom.SysException;
import com.njydsz.message.domain.entity.core.MsgLog;
import com.njydsz.message.domain.entity.core.MsgNotification;
import com.njydsz.message.domain.enums.receipt.ReceiptStatusEnum;
import com.njydsz.message.infra.mapper.core.MsgLogMapper;
import com.njydsz.message.infra.mapper.core.MsgNotificationMapper;
import com.njydsz.message.server.realtime.RealtimePushService;
import com.njydsz.message.server.service.core.DeliveryTimeOptimizer;
import com.njydsz.message.server.service.receipt.ReadStatusSyncService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 已读状态同步服务实现。
 *
 * <p>将 IM 渠道（企业微信/钉钉/飞书）的已读回执同步至消息中心状态，
 *
 * <p>供 {@code MsgLog.receiptStatus} 字段实时更新。
 *
 * @author ydsz-team
 * @since 1.0.0
 */

@Slf4j
@Service
@RequiredArgsConstructor
public class ReadStatusSyncServiceImpl implements ReadStatusSyncService {

    /** 消息日志 Mapper */
    private final MsgLogMapper msgLogMapper;
    /** 站内通知 Mapper */
    private final MsgNotificationMapper msgNotificationMapper;
    /** 实时推送服务（已读状态变更通知） */
    private final RealtimePushService realtimePushService;
    /** 智能推送时间优化器（记录用户活跃行为） */
    private final DeliveryTimeOptimizer deliveryTimeOptimizer;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean markRead(String msgId, String userId) {
        if (!StringUtils.hasText(msgId) || !StringUtils.hasText(userId)) {
            throw new SysException(BaseResultCode.BAD_REQUEST, "消息 ID 和用户 ID 不能为空");
        }
        // 更新消息日志的 receipt_status
        int updated = msgLogMapper.update(null, new LambdaUpdateWrapper<MsgLog>()
                .eq(MsgLog::getMsgId, msgId)
                .eq(MsgLog::getReceiver, userId)
                .ne(MsgLog::getReceiptStatus, ReceiptStatusEnum.READ.name())
                .set(MsgLog::getReceiptStatus, ReceiptStatusEnum.READ.name())
                .set(MsgLog::getReceiptAt, LocalDateTime.now()));

        if (updated > 0) {
            // 推送已读状态变更到前端
            realtimePushService.pushToUser(userId, "MESSAGE_READ",
                    Map.of("msgId", msgId, "status", "READ", "timestamp", System.currentTimeMillis()));
            // 记录用户活跃行为（供智能推送时间优化使用）
            deliveryTimeOptimizer.recordActivity(userId, null);
            log.info("[ReadStatus] 消息已读: msgId={} user={}", msgId, userId);
        }
        return updated > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int markReadBatch(List<String> msgIds, String userId) {
        if (msgIds == null || msgIds.isEmpty() || !StringUtils.hasText(userId)) {
            return 0;
        }
        int updated = msgLogMapper.update(null, new LambdaUpdateWrapper<MsgLog>()
                .in(MsgLog::getMsgId, msgIds)
                .eq(MsgLog::getReceiver, userId)
                .ne(MsgLog::getReceiptStatus, ReceiptStatusEnum.READ.name())
                .set(MsgLog::getReceiptStatus, ReceiptStatusEnum.READ.name())
                .set(MsgLog::getReceiptAt, LocalDateTime.now()));

        if (updated > 0) {
            // 推送批量已读状态到前端
            realtimePushService.pushToUser(userId, "MESSAGE_READ_BATCH",
                    Map.of("msgIds", msgIds, "count", updated, "timestamp", System.currentTimeMillis()));
            deliveryTimeOptimizer.recordActivity(userId, null);
            log.info("[ReadStatus] 批量消息已读: user={} count={}", userId, updated);
        }
        return updated;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean markNotificationRead(String notificationId, String userId) {
        if (!StringUtils.hasText(notificationId) || !StringUtils.hasText(userId)) {
            throw new SysException(BaseResultCode.BAD_REQUEST, "通知 ID 和用户 ID 不能为空");
        }
        int updated = msgNotificationMapper.update(null, new LambdaUpdateWrapper<MsgNotification>()
                .eq(MsgNotification::getId, notificationId)
                .eq(MsgNotification::getReceiverId, userId)
                .eq(MsgNotification::getReadStatus, 0)
                .set(MsgNotification::getReadStatus, 1)
                .set(MsgNotification::getReadTime, LocalDateTime.now()));

        if (updated > 0) {
            realtimePushService.pushToUser(userId, "NOTIFICATION_READ",
                    Map.of("notificationId", notificationId, "status", "READ"));
            deliveryTimeOptimizer.recordActivity(userId, "INAPP");
            log.info("[ReadStatus] 通知已读: id={} user={}", notificationId, userId);
        }
        return updated > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int markAllNotificationsRead(String userId, String bizType) {
        if (!StringUtils.hasText(userId)) {
            return 0;
        }
        LambdaUpdateWrapper<MsgNotification> wrapper = new LambdaUpdateWrapper<MsgNotification>()
                .eq(MsgNotification::getReceiverId, userId)
                .eq(MsgNotification::getReadStatus, 0)
                .eq(MsgNotification::getRecallStatus, "NONE")
                .set(MsgNotification::getReadStatus, 1)
                .set(MsgNotification::getReadTime, LocalDateTime.now());
        if (StringUtils.hasText(bizType)) {
            wrapper.eq(MsgNotification::getBizType, bizType);
        }
        int updated = msgNotificationMapper.update(null, wrapper);
        if (updated > 0) {
            realtimePushService.pushToUser(userId, "NOTIFICATION_READ_ALL",
                    Map.of("count", updated, "bizType", bizType == null ? "ALL" : bizType));
            deliveryTimeOptimizer.recordActivity(userId, "INAPP");
            log.info("[ReadStatus] 全部通知已读: user={} bizType={} count={}", userId, bizType, updated);
        }
        return updated;
    }

    @Override
    public long getUnreadCount(String userId) {
        if (!StringUtils.hasText(userId)) {
            return 0;
        }
        // 站内通知未读数
        Long notifCount = msgNotificationMapper.selectCount(
                new LambdaQueryWrapper<MsgNotification>()
                        .eq(MsgNotification::getReceiverId, userId)
                        .eq(MsgNotification::getReadStatus, 0)
                        .eq(MsgNotification::getRecallStatus, "NONE"));
        return notifCount == null ? 0 : notifCount;
    }

    @Override
    public long getUnreadCountByChannel(String userId, String channel) {
        if (!StringUtils.hasText(userId)) {
            return 0;
        }
        if (!StringUtils.hasText(channel)) {
            return getUnreadCount(userId);
        }
        // 站内通知按通道查询（站内信通道）
        if ("INAPP".equalsIgnoreCase(channel)) {
            return getUnreadCount(userId);
        }
        // 其他通道按消息日志查询 receipt_status != READ
        Long count = msgLogMapper.selectCount(
                new LambdaQueryWrapper<MsgLog>()
                        .eq(MsgLog::getReceiver, userId)
                        .eq(MsgLog::getChannel, channel.toUpperCase())
                        .ne(MsgLog::getReceiptStatus, ReceiptStatusEnum.READ.name())
                        .ne(MsgLog::getStatus, "FAILED"));
        return count == null ? 0 : count;
    }
}
