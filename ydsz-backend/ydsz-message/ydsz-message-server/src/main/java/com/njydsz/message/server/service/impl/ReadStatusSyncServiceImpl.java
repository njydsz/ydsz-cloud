package com.njydsz.message.server.service.impl.receipt;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.njydsz.common.core.code.BaseResultCode;
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
    /**
     * 标记单条消息已读。
     *
     * <p>事务内更新 {@code MsgLog.receipt_status} 为 READ（仅当状态非 READ 时才更新，保证幂等），
     * 并记录回执时间与实时推送已读事件。参数缺失抛出 {@code SysException}(BAD_REQUEST)。
     *
     * @param msgId  消息 ID
     * @param userId 用户 ID（须与消息接收人一致）
     * @return true 表示状态发生变更（即本次真正标记已读）
     * @throws com.njydsz.common.exception.custom.SysException msgId 或 userId 为空时
     */
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
    /**
     * 批量标记消息已读。
     *
     * <p>事务内对给定消息列表统一置 READ 并推送 {@code MESSAGE_READ_BATCH} 事件；
     * 空列表或 userId 缺失返回 0，不抛异常。
     *
     * @param msgIds 消息 ID 列表
     * @param userId 用户 ID
     * @return 实际更新的消息条数
     */
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
    /**
     * 标记单条站内通知已读。
     *
     * <p>事务内将 {@code MsgNotification.readStatus} 由 0 置 1（条件更新，幂等），记录已读时间并推送事件。
     *
     * @param notificationId 通知 ID
     * @param userId         用户 ID
     * @return true 表示状态发生变更
     * @throws com.njydsz.common.exception.custom.SysException notificationId 或 userId 为空时
     */
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
    /**
     * 标记用户全部站内通知已读（可按业务类型过滤）。
     *
     * <p>仅更新 {@code readStatus=0 且 recallStatus='NONE'} 的通知，避免把已撤回通知标记为已读；
     * 更新后推送 {@code NOTIFICATION_READ_ALL} 事件。userId 为空返回 0。
     *
     * @param userId  用户 ID
     * @param bizType 业务类型（可选，为空表示全部业务）
     * @return 实际更新的通知条数
     */
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

    /**
     * 查询用户站内未读通知总数。
     *
     * <p>统计 {@code readStatus=0 且 recallStatus='NONE'} 的通知数量；userId 为空返回 0，查询结果 null 视为 0。
     *
     * @param userId 用户 ID
     * @return 未读通知数（>=0）
     */
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

    /**
     * 按通道查询用户未读数。
     *
     * <p>站内信（INAPP）走通知表统计；其他通道按 {@code MsgLog} 中 {@code receipt_status != READ 且 status != FAILED} 统计。
     * channel 为空时退化为 {@link #getUnreadCount}。
     *
     * @param userId  用户 ID
     * @param channel 通道编码（如 SMS/EMAIL/INAPP）
     * @return 该通道未读消息数（>=0）
     */
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
