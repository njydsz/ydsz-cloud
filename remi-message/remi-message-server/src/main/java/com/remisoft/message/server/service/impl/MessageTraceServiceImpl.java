package com.remisoft.message.server.service.impl.core;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.remisoft.common.security.TenantContext;
import com.remisoft.common.util.id.TracerUtils;
import com.remisoft.common.json.RemiJson;
import com.remisoft.message.domain.entity.config.MsgTrace;
import com.remisoft.message.domain.entity.config.MsgTrace.Node;
import com.remisoft.message.infra.mapper.config.MsgTraceMapper;
import com.remisoft.message.server.service.core.MessageTraceService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 消息全链路追踪服务实现。
 *
 * <p>记录消息从创建 → 模板渲染 → 渠道发送 → 送达回执 → 用户点击的全链路事件 ({@code remi_msg_trace})。
 *
 * <p>每条事件携带 TraceId 与 ProviderTraceId，可与 SkyWalking/OpenTelemetry 关联。
 *
 * @author remi-team
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
            MsgTrace trace = new MsgTrace();
            trace.setMsgId(msgId);
            trace.setTraceId(TracerUtils.getOrCreateTraceId());
            trace.setNode(node.name());
            trace.setStatus(status == null ? "SUCCESS" : status);
            trace.setChannel(channel);
            trace.setMessage(message);
            trace.setEventAt(LocalDateTime.now());
            trace.setTenantId(TenantContext.getTenantId());
            if (extra != null && !extra.isEmpty()) {
                trace.setExtra(RemiJson.toJson(extra));
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
    public List<MsgTrace> getTraceByMsgId(String msgId) {
        if (!StringUtils.hasText(msgId)) {
            return List.of();
        }
        LambdaQueryWrapper<MsgTrace> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MsgTrace::getMsgId, msgId)
                .orderByAsc(MsgTrace::getEventAt);
        return msgTraceMapper.selectList(wrapper);
    }

    @Override
    public List<MsgTrace> getTraceByTraceId(String traceId) {
        if (!StringUtils.hasText(traceId)) {
            return List.of();
        }
        LambdaQueryWrapper<MsgTrace> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MsgTrace::getTraceId, traceId)
                .orderByAsc(MsgTrace::getEventAt);
        return msgTraceMapper.selectList(wrapper);
    }

    @Override
    public List<MsgTrace> getTraceByBiz(String bizType, String bizId) {
        if (!StringUtils.hasText(bizType) || !StringUtils.hasText(bizId)) {
            return List.of();
        }
        LambdaQueryWrapper<MsgTrace> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MsgTrace::getBizType, bizType)
                .eq(MsgTrace::getBizId, bizId)
                .orderByAsc(MsgTrace::getEventAt);
        return msgTraceMapper.selectList(wrapper);
    }
}
