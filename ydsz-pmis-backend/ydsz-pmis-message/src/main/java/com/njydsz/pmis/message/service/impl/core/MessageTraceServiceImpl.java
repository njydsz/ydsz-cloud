package com.njydsz.pmis.message.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.njydsz.pmis.common.security.TenantContext;
import com.njydsz.pmis.common.util.JsonUtils;
import com.njydsz.pmis.common.util.TraceIdUtil;
import com.njydsz.pmis.message.entity.config.MsgTraceDO;
import com.njydsz.pmis.message.entity.config.MsgTraceDO.Node;
import com.njydsz.pmis.message.mapper.config.MsgTraceMapper;
import com.njydsz.pmis.message.service.core.MessageTraceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * P0-2: 消息端到端追踪服务实现。
 *
 * <p>异步写入轨迹记录，不影响消息发送主流程性能。
 * 轨迹记录失败时仅记日志，不抛异常。
 *
 * @author ydsz-pmis-team
 * @since 1.3.0
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
            trace.setTraceId(TraceIdUtil.getOrCreate());
            trace.setNode(node.name());
            trace.setStatus(status == null ? "SUCCESS" : status);
            trace.setChannel(channel);
            trace.setMessage(message);
            trace.setEventAt(LocalDateTime.now());
            trace.setTenantId(TenantContext.getTenantId());
            if (extra != null && !extra.isEmpty()) {
                trace.setExtra(JsonUtils.toJson(extra));
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
