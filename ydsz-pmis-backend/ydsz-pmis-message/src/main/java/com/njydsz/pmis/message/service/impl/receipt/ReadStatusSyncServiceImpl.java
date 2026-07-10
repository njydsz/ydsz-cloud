package com.njydsz.pmis.message.service.impl.receipt;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.message.entity.core.MsgLogDO;
import com.njydsz.pmis.message.entity.core.MsgNotificationDO;
import com.njydsz.pmis.message.enums.receipt.ReceiptStatusEnum;
import com.njydsz.pmis.message.mapper.core.MsgLogMapper;
import com.njydsz.pmis.message.mapper.core.MsgNotificationMapper;
import com.njydsz.pmis.message.realtime.RealtimePushService;
import com.njydsz.pmis.message.service.core.DeliveryTimeOptimizer;
import com.njydsz.pmis.message.service.receipt.ReadStatusSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * P1-3: 全通道消息已读/未读状态同步服务实现。
 *
 * <p>统一管理消息已读状态的更新和实时同步：
 * <ul>
 *   <li>更新消息日志的 receipt_status 为 READ</li>
 *   <li>更新站内通知的 read_status 为 1</li>
 *   <li>通过 WebSocket 推送已读状态变更事件</li>
 *   <li>记录用户活跃行为（供智能推送时间优化使用）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.3.0
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
            throw new BizException(BizErrorCode.BAD_REQUEST, "消息 ID 和用户 ID 不能为空");
        }
        // 更新消息日志的 receipt_status
        int updated = msgLogMapper.update(null, new LambdaUpdateWrapper<MsgLogDO>()
                .eq(MsgLogDO::getMsgId, msgId)
                .eq(MsgLogDO::getReceiver, userId)
                .ne(MsgLogDO::getReceiptStatus, ReceiptStatusEnum.READ.name())
                .set(MsgLogDO::getReceiptStatus, ReceiptStatusEnum.READ.name())
                .set(MsgLogDO::getReceiptAt, LocalDateTime.now()));

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
        int updated = msgLogMapper.update(null, new LambdaUpdateWrapper<MsgLogDO>()
                .in(MsgLogDO::getMsgId, msgIds)
                .eq(MsgLogDO::getReceiver, userId)
                .ne(MsgLogDO::getReceiptStatus, ReceiptStatusEnum.READ.name())
                .set(MsgLogDO::getReceiptStatus, ReceiptStatusEnum.READ.name())
                .set(MsgLogDO::getReceiptAt, LocalDateTime.now()));

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
            throw new BizException(BizErrorCode.BAD_REQUEST, "通知 ID 和用户 ID 不能为空");
        }
        int updated = msgNotificationMapper.update(null, new LambdaUpdateWrapper<MsgNotificationDO>()
                .eq(MsgNotificationDO::getId, notificationId)
                .eq(MsgNotificationDO::getReceiverId, userId)
                .eq(MsgNotificationDO::getReadStatus, 0)
                .set(MsgNotificationDO::getReadStatus, 1)
                .set(MsgNotificationDO::getReadTime, LocalDateTime.now()));

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
        LambdaUpdateWrapper<MsgNotificationDO> wrapper = new LambdaUpdateWrapper<MsgNotificationDO>()
                .eq(MsgNotificationDO::getReceiverId, userId)
                .eq(MsgNotificationDO::getReadStatus, 0)
                .eq(MsgNotificationDO::getRecallStatus, "NONE")
                .set(MsgNotificationDO::getReadStatus, 1)
                .set(MsgNotificationDO::getReadTime, LocalDateTime.now());
        if (StringUtils.hasText(bizType)) {
            wrapper.eq(MsgNotificationDO::getBizType, bizType);
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
                new LambdaQueryWrapper<MsgNotificationDO>()
                        .eq(MsgNotificationDO::getReceiverId, userId)
                        .eq(MsgNotificationDO::getReadStatus, 0)
                        .eq(MsgNotificationDO::getRecallStatus, "NONE"));
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
                new LambdaQueryWrapper<MsgLogDO>()
                        .eq(MsgLogDO::getReceiver, userId)
                        .eq(MsgLogDO::getChannel, channel.toUpperCase())
                        .ne(MsgLogDO::getReceiptStatus, ReceiptStatusEnum.READ.name())
                        .ne(MsgLogDO::getStatus, "FAILED"));
        return count == null ? 0 : count;
    }
}
