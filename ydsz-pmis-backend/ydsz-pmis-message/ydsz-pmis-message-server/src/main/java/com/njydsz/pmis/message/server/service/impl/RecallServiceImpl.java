package com.njydsz.pmis.message.server.service.impl.receipt;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.njydsz.pmis.common.core.response.StandardResultCode;
import com.njydsz.pmis.common.exception.custom.SysException;
import com.njydsz.pmis.message.domain.entity.core.MsgLogDO;
import com.njydsz.pmis.message.domain.entity.core.MsgNotificationDO;
import com.njydsz.pmis.message.domain.entity.config.MsgTraceDO;
import com.njydsz.pmis.message.domain.enums.receipt.RecallStatusEnum;
import com.njydsz.pmis.message.infra.mapper.core.MsgLogMapper;
import com.njydsz.pmis.message.infra.mapper.core.MsgNotificationMapper;
import com.njydsz.pmis.message.server.realtime.RealtimePushService;
import com.njydsz.pmis.message.server.service.core.MessageLogService;
import com.njydsz.pmis.message.server.service.core.MessageTraceService;
import com.njydsz.pmis.message.server.service.receipt.RecallService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 消息撤回服务实现�? *
 * <p>撤回通知校验归属后更�?recall_status=RECALLED 并推送前�?撤回消息委托 {@link MessageLogService#markRecalled};
 * 批量撤回�?bizType+bizId 统计受影响条数�? *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecallServiceImpl implements RecallService {

    /** 站内通知 Mapper */
    private final MsgNotificationMapper msgNotificationMapper;
    /** 消息日志 Mapper */
    private final MsgLogMapper msgLogMapper;
    /** 实时推送服务（撤回通知推送） */
    private final RealtimePushService realtimePushService;
    /** 消息日志服务（撤回状态更新） */
    private final MessageLogService messageLogService;
    /** 消息全链路追踪服�?*/
    private final MessageTraceService messageTraceService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean recallNotification(String userId, String notificationId) {
        if (!StringUtils.hasText(userId) || !StringUtils.hasText(notificationId)) {
            throw new SysException(StandardResultCode.BAD_REQUEST, "用户 ID 与通知 ID 不能为空");
        }
        MsgNotificationDO n = msgNotificationMapper.selectById(notificationId);
        if (n == null) {
            throw new SysException(StandardResultCode.NOT_FOUND, "通知不存�? " + notificationId);
        }
        if (!userId.equals(n.getReceiverId())) {
            throw new SysException(StandardResultCode.FORBIDDEN, "仅可撤回本人的通知");
        }
        n.setRecallStatus(RecallStatusEnum.RECALLED.name());
        n.setRecallAt(LocalDateTime.now());
        msgNotificationMapper.updateById(n);
        // 推送撤回事件到前端
        realtimePushService.pushToUser(userId, "NOTIFICATION_RECALL", notificationId);
        log.info("[Recall] 撤回通知: id={} user={}", notificationId, userId);
        return true;
    }

    @Override
    public boolean recallMessage(String logId) {
        if (!StringUtils.hasText(logId)) {
            throw new SysException(StandardResultCode.BAD_REQUEST, "日志 ID 不能为空");
        }
        messageLogService.markRecalled(logId);
        // P0-4: 查找消息并通过 WebSocket 推送撤回事�?        MsgLogDO logDO = msgLogMapper.selectById(logId);
        if (logDO != null && StringUtils.hasText(logDO.getReceiver())) {
            realtimePushService.pushToUser(logDO.getReceiver(), "MESSAGE_RECALL", logDO.getMsgId());
            // P0-2: 记录撤回轨迹
            messageTraceService.recordTrace(logDO.getMsgId(),
                    MsgTraceDO.Node.RECALLED, "SUCCESS", logDO.getChannel(),
                    "消息已撤�? logId=" + logId);
        }
        log.info("[Recall] 撤回消息: logId={}", logId);
        return true;
    }

    /**
     * P0-4: �?msgId 撤回已发送消息�?     *
     * <p>校验撤回时间窗口（默�?30 分钟），超时不可撤回�?     * 撤回后更新状态为 RECALLED 并推送前端撤回事件�?     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean recallByMsgId(String msgId) {
        if (!StringUtils.hasText(msgId)) {
            throw new SysException(StandardResultCode.BAD_REQUEST, "消息 ID 不能为空");
        }
        // �?msgId 查询消息日志
        MsgLogDO logDO = msgLogMapper.selectOne(new LambdaQueryWrapper<MsgLogDO>()
                .eq(MsgLogDO::getMsgId, msgId)
                .last("LIMIT 1"));
        if (logDO == null) {
            throw new SysException(StandardResultCode.NOT_FOUND, "消息不存�? msgId=" + msgId);
        }
        // 校验撤回时间窗口
        if (logDO.getCreatedAt() != null) {
            long minutesElapsed = java.time.Duration.between(
                    logDO.getCreatedAt(), java.time.LocalDateTime.now()).toMinutes();
            if (minutesElapsed > RECALL_WINDOW_MINUTES) {
                throw new SysException(StandardResultCode.BIZ_ERROR,
                        "消息发送已超过 " + RECALL_WINDOW_MINUTES + " 分钟，不可撤�?);
            }
        }
        // 校验是否已撤�?        if (RecallStatusEnum.RECALLED.name().equals(logDO.getRecallStatus())) {
            throw new SysException(StandardResultCode.BIZ_ERROR, "消息已撤回，无需重复操作");
        }
        // 执行撤回
        logDO.setRecallStatus(RecallStatusEnum.RECALLED.name());
        logDO.setRecallAt(java.time.LocalDateTime.now());
        msgLogMapper.updateById(logDO);
        // 推送撤回事件到前端
        if (StringUtils.hasText(logDO.getReceiver())) {
            realtimePushService.pushToUser(logDO.getReceiver(), "MESSAGE_RECALL", msgId);
        }
        // P0-2: 记录撤回轨迹
        messageTraceService.recordTrace(msgId, MsgTraceDO.Node.RECALLED,
                "SUCCESS", logDO.getChannel(), "消息已撤�? msgId=" + msgId);
        log.info("[Recall] �?msgId 撤回成功: msgId={} channel={}", msgId, logDO.getChannel());
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int recallBatch(String bizType, String bizId) {
        if (!StringUtils.hasText(bizType) || !StringUtils.hasText(bizId)) {
            throw new SysException(StandardResultCode.BAD_REQUEST, "业务类型与单�?ID 不能为空");
        }
        // 通知批量撤回
        int notifCount = msgNotificationMapper.update(null, new LambdaUpdateWrapper<MsgNotificationDO>()
                .eq(MsgNotificationDO::getBizType, bizType)
                .eq(MsgNotificationDO::getBizId, bizId)
                .eq(MsgNotificationDO::getRecallStatus, RecallStatusEnum.NONE.name())
                .set(MsgNotificationDO::getRecallStatus, RecallStatusEnum.RECALLED.name())
                .set(MsgNotificationDO::getRecallAt, LocalDateTime.now()));
        // 消息日志批量撤回（仅更新非终态）
        int logCount = msgLogMapper.update(null, new LambdaUpdateWrapper<MsgLogDO>()
                .eq(MsgLogDO::getBizType, bizType)
                .eq(MsgLogDO::getBizId, bizId)
                .eq(MsgLogDO::getRecallStatus, RecallStatusEnum.NONE.name())
                .set(MsgLogDO::getRecallStatus, RecallStatusEnum.RECALLED.name())
                .set(MsgLogDO::getRecallAt, LocalDateTime.now()));
        log.info("[Recall] 批量撤回: bizType={} bizId={} notif={} log={}", bizType, bizId, notifCount, logCount);
        return notifCount + logCount;
    }
}
