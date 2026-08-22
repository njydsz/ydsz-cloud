package com.njydsz.message.server.service.impl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.njydsz.common.json.YdszJson;
import com.njydsz.common.tenant.TenantContextHolder;
import com.njydsz.common.util.id.TracerUtils;
import com.njydsz.message.domain.query.MsgTraceQuery;
import com.njydsz.message.domain.repository.MsgTraceRepository;
import com.njydsz.message.domain.vo.MsgTraceVO;
import com.njydsz.message.server.service.core.MessageTraceService;

/**
 * 消息全链路追踪服务实现。
 *
 * <p>记录消息从创建 → 模板渲染 → 渠道发送 → 送达回执 → 用户点击的全链路事件 ({@code ydsz_msg_trace})。
 *
 * <p>每条事件携带 TraceId 与 ProviderTraceId，可与 SkyWalking/OpenTelemetry 关联。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageTraceServiceImpl implements MessageTraceService {

  /** 消息轨迹 Repository（异步写入） */
  private final MsgTraceRepository msgTraceRepository;

  @Override
  @Async
  public void recordTrace(
      String msgId,
      String node,
      String status,
      String channel,
      String message,
      Map<String, Object> extra) {
    if (!StringUtils.hasText(msgId) || !StringUtils.hasText(node)) {
      return;
    }
    try {
      MsgTraceVO trace = new MsgTraceVO();
      trace.setMsgId(msgId);
      trace.setTraceId(TracerUtils.getOrCreateTraceId());
      trace.setNode(node);
      trace.setStatus(status == null ? "SUCCESS" : status);
      trace.setChannel(channel);
      trace.setMessage(message);
      trace.setEventAt(LocalDateTime.now());
      trace.setTenantId(TenantContextHolder.getTenantId());
      if (extra != null && !extra.isEmpty()) {
        trace.setExtra(YdszJson.toJson(extra));
      }
      msgTraceRepository.save(trace);
      log.debug("[Trace] 记录轨迹: msgId={} node={} status={}", msgId, node, status);
    } catch (Exception e) {
      log.warn("[Trace] 记录轨迹失败,不影响主流程: msgId={} node={} err={}", msgId, node, e.getMessage());
    }
  }

  @Override
  @Async
  public void recordTrace(String msgId, String node, String status, String channel, String message) {
    recordTrace(msgId, node, status, channel, message, null);
  }

  @Override
  public List<MsgTraceVO> getTraceByMsgId(String msgId) {
    if (!StringUtils.hasText(msgId)) {
      return List.of();
    }
    MsgTraceQuery query = new MsgTraceQuery();
    query.setMsgId(msgId);
    return msgTraceRepository.findList(query);
  }

  @Override
  public List<MsgTraceVO> getTraceByTraceId(String traceId) {
    if (!StringUtils.hasText(traceId)) {
      return List.of();
    }
    MsgTraceQuery query = new MsgTraceQuery();
    query.setTraceId(traceId);
    return msgTraceRepository.findList(query);
  }

  @Override
  public List<MsgTraceVO> getTraceByBiz(String bizType, String bizId) {
    if (!StringUtils.hasText(bizType) || !StringUtils.hasText(bizId)) {
      return List.of();
    }
    MsgTraceQuery query = new MsgTraceQuery();
    query.setBizType(bizType);
    query.setBizId(bizId);
    return msgTraceRepository.findList(query);
  }
}
