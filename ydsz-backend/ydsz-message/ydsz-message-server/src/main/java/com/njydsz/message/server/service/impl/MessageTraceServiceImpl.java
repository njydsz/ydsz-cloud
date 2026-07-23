package com.njydsz.message.server.service.impl.core;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.njydsz.common.security.TenantContext;
import com.njydsz.common.util.id.TracerUtils;
import com.njydsz.common.json.YdszJson;
import com.njydsz.message.domain.entity.config.MsgTraceDO;
import com.njydsz.message.domain.entity.config.MsgTraceDO.Node;
import com.njydsz.message.infra.mapper.config.MsgTraceMapper;
import com.njydsz.message.server.service.core.MessageTraceService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * P0-2: 消息端到端追踪服务实现。
 *
 * <p>异步写入轨迹记录，不影响消息发送主流程性能。
 * 轨迹记录失败时仅记日志，不抛异常。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageTraceServiceImpl implements MessageTraceService {

    /** 消息轨迹 Mapper（异步写入） */
    private final MsgTraceMapper msgTraceMapper;

    @Override
    @Async
    public void recordTrace(String msgId, Node node, String status, String channel,
                            String message, Map<String, Object> extra) {
        if (!StringUtils.hasText(msgId) || node == null) {
            return;
        }
        try {
            MsgTraceDO trace = new MsgTraceDO();
            trace.setMsgId(msgId);
            trace.setTraceId(TracerUtils.getOrCreateTraceId());
            trace.setNode(node.name());
            trace.setStatus(status == null ? "SUCCESS" : status);
            trace.setChannel(channel);
            trace.setMessage(message);
            trace.setEventAt(LocalDateTime.now());
            trace.setTenantId(TenantContext.getTenantId());
            if (extra != null && !extra.isEmpty()) {
                trace.setExtra(YdszJson.toJson(extra));
            }
            msgTraceMapper.insert(trace);
            log.debug("[Trace] 记录轨迹: msgId={} node={} status={}", msgId, node, status);
        } catch (Exception e) {
            log.warn("[Trace] 记录轨迹失败,不影响主流程: msgId={} node={} err={}",
                    msgId, node, e.getMessage());
        }
    }

    @Override
    @Async
    public void recordTrace(String msgId, Node node, String status, String channel, String message) {
        recordTrace(msgId, node, status, channel, message, null);
    }

    @Override
    public List<MsgTraceDO> getTraceByMsgId(String msgId) {
        if (!StringUtils.hasText(msgId)) {
            return List.of();
        }
        LambdaQueryWrapper<MsgTraceDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MsgTraceDO::getMsgId, msgId)
                .orderByAsc(MsgTraceDO::getEventAt);
        return msgTraceMapper.selectList(wrapper);
    }

    @Override
    public List<MsgTraceDO> getTraceByTraceId(String traceId) {
        if (!StringUtils.hasText(traceId)) {
            return List.of();
        }
        LambdaQueryWrapper<MsgTraceDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MsgTraceDO::getTraceId, traceId)
                .orderByAsc(MsgTraceDO::getEventAt);
        return msgTraceMapper.selectList(wrapper);
    }

    @Override
    public List<MsgTraceDO> getTraceByBiz(String bizType, String bizId) {
        if (!StringUtils.hasText(bizType) || !StringUtils.hasText(bizId)) {
            return List.of();
        }
        LambdaQueryWrapper<MsgTraceDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MsgTraceDO::getBizType, bizType)
                .eq(MsgTraceDO::getBizId, bizId)
                .orderByAsc(MsgTraceDO::getEventAt);
        return msgTraceMapper.selectList(wrapper);
    }
}
