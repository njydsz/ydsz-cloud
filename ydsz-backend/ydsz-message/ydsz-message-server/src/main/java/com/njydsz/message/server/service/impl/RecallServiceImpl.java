package com.njydsz.message.server.service.impl.receipt;

import java.time.Duration;
import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.njydsz.common.core.code.BaseResultCode;
import com.njydsz.common.exception.custom.SysException;
import com.njydsz.message.domain.entity.config.MsgTrace;
import com.njydsz.message.domain.entity.core.MsgLog;
import com.njydsz.message.domain.entity.core.MsgNotification;
import com.njydsz.message.domain.enums.receipt.RecallStatusEnum;
import com.njydsz.message.infra.mapper.core.MsgLogMapper;
import com.njydsz.message.infra.mapper.core.MsgNotificationMapper;
import com.njydsz.message.server.realtime.RealtimePushService;
import com.njydsz.message.server.service.core.MessageLogService;
import com.njydsz.message.server.service.core.MessageTraceService;
import com.njydsz.message.server.service.impl.MessageRecallPushService;
import com.njydsz.message.server.service.receipt.RecallService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

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

    /** 站内通知 Mapper */
    private final MsgNotificationMapper msgNotificationMapper;
    /** 消息日志 Mapper */
    private final MsgLogMapper msgLogMapper;
    /** 实时推送服务（撤回通知推送） */
    private final RealtimePushService realtimePushService;
    /** P2-19: 消息撤回实时推送（携带撤回原因/时间戳等元数据） */
    private final MessageRecallPushService messageRecallPushService;
    /** 消息日志服务（撤回状态更新） */
    private final MessageLogService messageLogService;
    /** 消息全链路追踪服务 */
    private final MessageTraceService messageTraceService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean recallNotification(String userId, String notificationId) {
        if (!StringUtils.hasText(userId) || !StringUtils.hasText(notificationId)) {
            throw new SysException(BaseResultCode.BAD_REQUEST, "用户 ID 与通知 ID 不能为空");
        }
        MsgNotification n = msgNotificationMapper.selectById(notificationId);
        if (n == null) {
            throw new SysException(BaseResultCode.NOT_FOUND, "通知不存在: " + notificationId);
        }
        if (!userId.equals(n.getReceiverId())) {
            throw new SysException(BaseResultCode.FORBIDDEN, "仅可撤回本人的通知");
        }
        n.setRecallStatus(RecallStatusEnum.RECALLED.name());
        n.setRecallAt(LocalDateTime.now());
        msgNotificationMapper.updateById(n);
        // P2-19: 推送撤回事件到前端（携带撤回原因/时间戳）
        messageRecallPushService.pushRecall(userId, notificationId, "通知撤回");
        log.info("[Recall] 撤回通知: id={} user={}", notificationId, userId);
        return true;
    }

    @Override
    public boolean recallMessage(String logId) {
        if (!StringUtils.hasText(logId)) {
            throw new SysException(BaseResultCode.BAD_REQUEST, "日志 ID 不能为空");
        }
        messageLogService.markRecalled(logId);
        // P0-4: 查找消息并通过 WebSocket 推送撤回事件
        MsgLog logDO = msgLogMapper.selectById(logId);
        if (logDO != null && StringUtils.hasText(logDO.getReceiver())) {
            // P2-19: 推送撤回事件（携带消息 ID/撤回原因/时间戳）
            messageRecallPushService.pushRecall(logDO.getReceiver(), logDO.getMsgId(), "消息撤回");
            // P0-2: 记录撤回轨迹
            messageTraceService.recordTrace(logDO.getMsgId(),
                    MsgTrace.Node.RECALLED, "SUCCESS", logDO.getChannel(),
                    "消息已撤回: logId=" + logId);
        }
        log.info("[Recall] 撤回消息: logId={}", logId);
        return true;
    }

    /**
     * P0-4: 按 msgId 撤回已发送消息。
     *
     * <p>校验撤回时间窗口（默认 30 分钟），超时不可撤回。
     * 撤回后更新状态为 RECALLED 并推送前端撤回事件。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean recallByMsgId(String msgId) {
        if (!StringUtils.hasText(msgId)) {
            throw new SysException(BaseResultCode.BAD_REQUEST, "消息 ID 不能为空");
        }
        // 按 msgId 查询消息日志
        MsgLog logDO = msgLogMapper.selectOne(new LambdaQueryWrapper<MsgLog>()
                .eq(MsgLog::getMsgId, msgId)
                .last("LIMIT 1"));
        if (logDO == null) {
            throw new SysException(BaseResultCode.NOT_FOUND, "消息不存在: msgId=" + msgId);
        }
        // 校验撤回时间窗口
        if (logDO.getCreatedAt() != null) {
            long minutesElapsed = Duration.between(
                    logDO.getCreatedAt(), LocalDateTime.now()).toMinutes();
            if (minutesElapsed > RECALL_WINDOW_MINUTES) {
                throw new SysException(BaseResultCode.BIZ_ERROR,
                        "消息发送已超过 " + RECALL_WINDOW_MINUTES + " 分钟，不可撤回");
            }
        }
        // 校验是否已撤回
        if (RecallStatusEnum.RECALLED.name().equals(logDO.getRecallStatus())) {
            throw new SysException(BaseResultCode.BIZ_ERROR, "消息已撤回，无需重复操作");
        }
        // 执行撤回
        logDO.setRecallStatus(RecallStatusEnum.RECALLED.name());
        logDO.setRecallAt(LocalDateTime.now());
        msgLogMapper.updateById(logDO);
        // P2-19: 推送撤回事件到前端（携带撤回原因/时间戳）
        if (StringUtils.hasText(logDO.getReceiver())) {
            messageRecallPushService.pushRecall(logDO.getReceiver(), msgId, "消息撤回");
        }
        // P0-2: 记录撤回轨迹
        messageTraceService.recordTrace(msgId, MsgTrace.Node.RECALLED,
                "SUCCESS", logDO.getChannel(), "消息已撤回: msgId=" + msgId);
        log.info("[Recall] 按 msgId 撤回成功: msgId={} channel={}", msgId, logDO.getChannel());
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int recallBatch(String bizType, String bizId) {
        if (!StringUtils.hasText(bizType) || !StringUtils.hasText(bizId)) {
            throw new SysException(BaseResultCode.BAD_REQUEST, "业务类型与单据 ID 不能为空");
        }
        // 通知批量撤回
        int notifCount = msgNotificationMapper.update(null, new LambdaUpdateWrapper<MsgNotification>()
                .eq(MsgNotification::getBizType, bizType)
                .eq(MsgNotification::getBizId, bizId)
                .eq(MsgNotification::getRecallStatus, RecallStatusEnum.NONE.name())
                .set(MsgNotification::getRecallStatus, RecallStatusEnum.RECALLED.name())
                .set(MsgNotification::getRecallAt, LocalDateTime.now()));
        // 消息日志批量撤回（仅更新非终态）
        int logCount = msgLogMapper.update(null, new LambdaUpdateWrapper<MsgLog>()
                .eq(MsgLog::getBizType, bizType)
                .eq(MsgLog::getBizId, bizId)
                .eq(MsgLog::getRecallStatus, RecallStatusEnum.NONE.name())
                .set(MsgLog::getRecallStatus, RecallStatusEnum.RECALLED.name())
                .set(MsgLog::getRecallAt, LocalDateTime.now()));
        log.info("[Recall] 批量撤回: bizType={} bizId={} notif={} log={}", bizType, bizId, notifCount, logCount);
        return notifCount + logCount;
    }
}
