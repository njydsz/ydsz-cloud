package com.njydsz.pmis.message.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.message.entity.MsgLogDO;
import com.njydsz.pmis.message.entity.MsgNotificationDO;
import com.njydsz.pmis.message.enums.RecallStatusEnum;
import com.njydsz.pmis.message.mapper.MsgLogMapper;
import com.njydsz.pmis.message.mapper.MsgNotificationMapper;
import com.njydsz.pmis.message.realtime.RealtimePushService;
import com.njydsz.pmis.message.service.MessageLogService;
import com.njydsz.pmis.message.service.RecallService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 消息撤回服务实现。
 *
 * <p>撤回通知校验归属后更新 recall_status=RECALLED 并推送前端;撤回消息委托 {@link MessageLogService#markRecalled};
 * 批量撤回按 bizType+bizId 统计受影响条数。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecallServiceImpl implements RecallService {

    private final MsgNotificationMapper msgNotificationMapper;
    private final MsgLogMapper msgLogMapper;
    private final RealtimePushService realtimePushService;
    private final MessageLogService messageLogService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean recallNotification(String userId, String notificationId) {
        if (!StringUtils.hasText(userId) || !StringUtils.hasText(notificationId)) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "用户 ID 与通知 ID 不能为空");
        }
        MsgNotificationDO n = msgNotificationMapper.selectById(notificationId);
        if (n == null) {
            throw new BizException(BizErrorCode.NOT_FOUND, "通知不存在: " + notificationId);
        }
        if (!userId.equals(n.getReceiverId())) {
            throw new BizException(BizErrorCode.FORBIDDEN, "仅可撤回本人的通知");
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
            throw new BizException(BizErrorCode.BAD_REQUEST, "日志 ID 不能为空");
        }
        messageLogService.markRecalled(logId);
        log.info("[Recall] 撤回消息: logId={}", logId);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int recallBatch(String bizType, String bizId) {
        if (!StringUtils.hasText(bizType) || !StringUtils.hasText(bizId)) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "业务类型与单据 ID 不能为空");
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
