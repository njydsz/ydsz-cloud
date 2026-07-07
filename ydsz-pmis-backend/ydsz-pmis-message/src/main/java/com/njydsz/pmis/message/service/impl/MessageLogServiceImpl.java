package com.njydsz.pmis.message.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.entity.PageQuery;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.message.dto.MessageLogQueryDTO;
import com.njydsz.pmis.message.entity.MsgLogDO;
import com.njydsz.pmis.message.enums.MessageStatusEnum;
import com.njydsz.pmis.message.enums.RecallStatusEnum;
import com.njydsz.pmis.message.mapper.MsgLogMapper;
import com.njydsz.pmis.message.service.MessageLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 消息发送日志服务实现。
 *
 * <p>状态流转必须经 {@link MessageStatusEnum#canTransitTo} 校验，非法流转抛 BizException。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageLogServiceImpl implements MessageLogService {

    private final MsgLogMapper msgLogMapper;

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

    private MessageStatusEnum parseStatus(String value) {
        try {
            return MessageStatusEnum.valueOf(value);
        } catch (Exception e) {
            throw new BizException(BizErrorCode.BIZ_ERROR, "非法消息状态: " + value);
        }
    }
}
