package com.njydsz.pmis.message.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.entity.PageQuery;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.message.channel.ChannelRouter;
import com.njydsz.pmis.message.config.MessageProperties;
import com.njydsz.pmis.message.config.RetryStrategyResolver;
import com.njydsz.pmis.message.dto.MessageLogQueryDTO;
import com.njydsz.pmis.message.entity.MsgLogDO;
import com.njydsz.pmis.message.enums.MessageStatusEnum;
import com.njydsz.pmis.message.enums.RecallStatusEnum;
import com.njydsz.pmis.message.event.DeadLetterAlertEvent;
import com.njydsz.pmis.message.mapper.MsgLogMapper;
import com.njydsz.pmis.message.metric.MessageMetrics;
import com.njydsz.pmis.message.service.MessageLogService;
import com.njydsz.pmis.message.tracing.MessageTraceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 消息发送日志服务实现。
 *
 * <p>状态流转必须经 {@link MessageStatusEnum#canTransitTo} 校验，非法流转抛 BizException。
 * 手动重发死信 ({@link #resendDead}) 为显式运维操作,绕过 canTransitTo 但仅限 DEAD 状态。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageLogServiceImpl implements MessageLogService {

    private final MsgLogMapper msgLogMapper;
    private final ChannelRouter channelRouter;
    private final RetryStrategyResolver retryStrategyResolver;
    private final ApplicationEventPublisher eventPublisher;
    private final MessageProperties messageProperties;
    private final MessageMetrics messageMetrics;

    /** P1-4: 通道 → 上次告警时间戳(ms),用于告警冷却去重 */
    private final ConcurrentHashMap<String, Long> lastAlertTimeMap = new ConcurrentHashMap<>();

    @Override
    public MsgLogDO getById(String id) {
        if (!StringUtils.hasText(id)) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "日志 ID 不能为空");
        }
        MsgLogDO entity = msgLogMapper.selectById(id);
        if (entity == null) {
            throw new BizException(BizErrorCode.NOT_FOUND, "日志不存在: " + id);
        }
        return entity;
    }

    @Override
    public Page<MsgLogDO> page(MessageLogQueryDTO query) {
        Page<MsgLogDO> page = new Page<>(
                query == null ? 1 : query.getPage(),
                Math.min(query == null ? 10 : query.getSize(), PageQuery.MAX_SIZE));
        LambdaQueryWrapper<MsgLogDO> w = new LambdaQueryWrapper<>();
        if (query != null) {
            w.eq(StringUtils.hasText(query.getChannel()), MsgLogDO::getChannel, query.getChannel());
            w.eq(StringUtils.hasText(query.getBizType()), MsgLogDO::getBizType, query.getBizType());
            w.eq(StringUtils.hasText(query.getBizId()), MsgLogDO::getBizId, query.getBizId());
            w.eq(StringUtils.hasText(query.getStatus()), MsgLogDO::getStatus, query.getStatus());
            w.eq(StringUtils.hasText(query.getReceiver()), MsgLogDO::getReceiver, query.getReceiver());
            w.eq(StringUtils.hasText(query.getPriority()), MsgLogDO::getPriority, query.getPriority());
            w.eq(StringUtils.hasText(query.getRecallStatus()), MsgLogDO::getRecallStatus, query.getRecallStatus());
            w.eq(StringUtils.hasText(query.getTenantId()), MsgLogDO::getTenantId, query.getTenantId());
        }
        w.orderByDesc(MsgLogDO::getCreatedAt);
        return msgLogMapper.selectPage(page, w);
    }

    @Override
    public void markRetry(String id, LocalDateTime nextRetryAt) {
        MsgLogDO entity = getById(id);
        MessageStatusEnum current = parseStatus(entity.getStatus());
        if (!current.canTransitTo(MessageStatusEnum.RETRY)) {
            throw new BizException(BizErrorCode.BIZ_ERROR,
                    "非法状态流转: " + current + " -> RETRY");
        }
        entity.setStatus(MessageStatusEnum.RETRY.name());
        entity.setNextRetryAt(nextRetryAt);
        entity.setRetryCount(entity.getRetryCount() == null ? 1 : entity.getRetryCount() + 1);
        msgLogMapper.updateById(entity);
        log.info("[MessageLog] 标记重试: id={} nextRetryAt={} retryCount={}", id, nextRetryAt, entity.getRetryCount());
    }

    @Override
    public void markDead(String id, String errorMessage) {
        MsgLogDO entity = getById(id);
        MessageStatusEnum current = parseStatus(entity.getStatus());
        if (!current.canTransitTo(MessageStatusEnum.DEAD)) {
            // 仅 RETRY 可流转到 DEAD；其他状态强制记录但仍校验，非法抛异常
            throw new BizException(BizErrorCode.BIZ_ERROR,
                    "非法状态流转: " + current + " -> DEAD");
        }
        entity.setStatus(MessageStatusEnum.DEAD.name());
        entity.setErrorMessage(errorMessage);
        msgLogMapper.updateById(entity);
        log.warn("[MessageLog] 标记死信: id={} err={}", id, errorMessage);
        // P1-4: 死信告警检测
        checkAndFireDeadLetterAlert(entity.getChannel());
    }

    @Override
    public void updateReceipt(String id, String receiptStatus, LocalDateTime receiptAt) {
        MsgLogDO entity = getById(id);
        entity.setReceiptStatus(receiptStatus);
        entity.setReceiptAt(receiptAt);
        msgLogMapper.updateById(entity);
    }

    @Override
    public void markRecalled(String id) {
        MsgLogDO entity = getById(id);
        MessageStatusEnum current = parseStatus(entity.getStatus());
        if (!current.canTransitTo(MessageStatusEnum.RECALLED)) {
            throw new BizException(BizErrorCode.BIZ_ERROR,
                    "非法状态流转: " + current + " -> RECALLED");
        }
        entity.setStatus(MessageStatusEnum.RECALLED.name());
        entity.setRecallStatus(RecallStatusEnum.RECALLED.name());
        entity.setRecallAt(LocalDateTime.now());
        msgLogMapper.updateById(entity);
    }

    /**
     * P1-4: 手动重发死信。
     *
     * <p>仅 DEAD 状态可重发。重置 retryCount / errorMessage / nextRetryAt，
     * 流转为 SENDING 后立即通过 {@link ChannelRouter#dispatch(MsgLogDO)} 重新投递。
     * 投递失败则进入 RETRY 状态（retryCount=1）走正常重试调度，而非立即再次死信。
     */
    @Override
    public void resendDead(String logId) {
        MsgLogDO entity = getById(logId);
        MessageStatusEnum current = parseStatus(entity.getStatus());
        if (current != MessageStatusEnum.DEAD) {
            throw new BizException(BizErrorCode.BIZ_ERROR,
                    "仅死信可手动重发,当前状态: " + current);
        }
        try (MessageTraceContext ctx = MessageTraceContext.enter(entity.getTraceId())) {
            // 重置重试上下文
            entity.setRetryCount(0);
            entity.setErrorMessage(null);
            entity.setNextRetryAt(null);
            entity.setStatus(MessageStatusEnum.SENDING.name());
            msgLogMapper.updateById(entity);
            log.info("[MessageLog] 手动重发死信: logId={} channel={}", logId, entity.getChannel());

            long start = System.currentTimeMillis();
            try {
                String providerTraceId = channelRouter.dispatch(entity);
                long cost = System.currentTimeMillis() - start;
                entity.setStatus(MessageStatusEnum.SUCCESS.name());
                entity.setProviderTraceId(providerTraceId);
                entity.setCostMs(cost);
                msgLogMapper.updateById(entity);
                messageMetrics.recordSend(entity.getChannel(), "SUCCESS", cost);
                log.info("[MessageLog] 死信重发成功: logId={} providerTraceId={}", logId, providerTraceId);
            } catch (Exception e) {
                long cost = System.currentTimeMillis() - start;
                int newRetryCount = 1;
                entity.setRetryCount(newRetryCount);
                entity.setCostMs(cost);
                entity.setErrorMessage(e.getMessage());
                // 进入正常重试调度,而非立即再次死信
                entity.setStatus(MessageStatusEnum.RETRY.name());
                entity.setNextRetryAt(retryStrategyResolver.calcNextRetryAt(newRetryCount, entity.getChannel()));
                msgLogMapper.updateById(entity);
                messageMetrics.recordRetry(entity.getChannel());
                log.warn("[MessageLog] 死信重发失败转重试: logId={} err={} nextRetryAt={}",
                        logId, e.getMessage(), entity.getNextRetryAt());
            }
        }
    }

    /**
     * P1-4: 死信告警检测。
     *
     * <p>统计窗口内指定通道的死信数量,达到阈值且通过冷却期则发布 {@link DeadLetterAlertEvent}。
     * 告警逻辑不抛异常,避免影响 markDead 主流程。
     *
     * @param channel 触发死信的通道
     */
    private void checkAndFireDeadLetterAlert(String channel) {
        try {
            if (!StringUtils.hasText(channel)) {
                return;
            }
            MessageProperties.DeadLetterAlertConfig cfg = messageProperties.getDeadLetterAlert();
            if (cfg == null || !cfg.isEnabled() || cfg.getThreshold() <= 0) {
                return;
            }
            // 冷却期去重:同一通道冷却期内不重复告警
            long now = System.currentTimeMillis();
            Long last = lastAlertTimeMap.get(channel);
            long cooldownMs = cfg.getCooldownMinutes() * 60_000L;
            if (last != null && (now - last) < cooldownMs) {
                return;
            }
            // 统计窗口内死信数量
            LocalDateTime windowStart = LocalDateTime.now().minusMinutes(cfg.getWindowMinutes());
            Long count = msgLogMapper.selectCount(new LambdaQueryWrapper<MsgLogDO>()
                    .eq(MsgLogDO::getStatus, MessageStatusEnum.DEAD.name())
                    .eq(MsgLogDO::getChannel, channel)
                    .ge(MsgLogDO::getCreatedAt, windowStart));
            long currentCount = count == null ? 0L : count;
            if (currentCount >= cfg.getThreshold()) {
                lastAlertTimeMap.put(channel, now);
                DeadLetterAlertEvent event = new DeadLetterAlertEvent(this, channel, currentCount,
                        cfg.getThreshold(), cfg.getWindowMinutes());
                eventPublisher.publishEvent(event);
                log.info("[MessageLog] 死信告警已触发: channel={} count={} threshold={}",
                        channel, currentCount, cfg.getThreshold());
            }
        } catch (Exception e) {
            log.error("[MessageLog] 死信告警检测异常,不影响主流程: {}", e.getMessage(), e);
        }
    }

    private MessageStatusEnum parseStatus(String value) {
        try {
            return MessageStatusEnum.valueOf(value);
        } catch (Exception e) {
            throw new BizException(BizErrorCode.BIZ_ERROR, "非法消息状态: " + value);
        }
    }
}
