package com.njydsz.message.server.service.chain.handler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.njydsz.common.feign.MessageRequest;
import com.njydsz.common.feign.MessageResult;
import com.njydsz.common.safe.sensitive.SensitiveUtil;
import com.njydsz.common.tenant.TenantContextHolder;
import com.njydsz.message.domain.constant.MessageConstants;
import com.njydsz.message.domain.enums.MessageExceptionCode;
import com.njydsz.message.domain.event.MessageSkippedEvent;
import com.njydsz.message.server.event.DomainEventPublisher;
import com.njydsz.message.server.metric.MessageMetrics;
import com.njydsz.message.server.service.chain.SendContext;
import com.njydsz.message.server.service.chain.SendHandler;
import com.njydsz.message.server.service.core.GuardService;

/**
 * 智能去重 Handler。
 *
 * <p>使用 Redis SET NX EX 原子去重，窗口内重复消息跳过发送。 去重 key 由 bizId + receiver + templateCode 拼接而成， 含 channel 时追加 channel 维度（P2-D3 去重精化）。
 *
 * @author ydsz-team
 * @since 1.1.0
 */
@Slf4j
@Component
@Order(600)
@RequiredArgsConstructor
public class DedupHandler implements SendHandler {

  private final GuardService guardService;
  private final MessageMetrics messageMetrics;
  private final DomainEventPublisher domainEventPublisher;

  @Override
  public boolean handle(MessageRequest request, SendContext ctx) {
    String dedupKey = buildDedupKey(request);
    if (!StringUtils.hasText(dedupKey)) {
      return true;
    }
    ctx.setDedupKey(dedupKey);
    if (!guardService.tryDedup(dedupKey)) {
      log.info(
          "[DedupHandler] 去重命中,跳过发送: dedupKey={}, receiver={}, templateCode={}, channel={}",
          dedupKey,
          SensitiveUtil.scanAndMask(ctx.getReceiver()),
          request.getTemplateCode(),
          request.getChannel());
      messageMetrics.recordSend(ctx.getChannel(), "DEDUPED", 0);
      // P2-A4: 发布消息被拦截领域事件
      domainEventPublisher.publish(
          new MessageSkippedEvent(
              TenantContextHolder.getTenantId(),
              request.getMessageId(),
              "DEDUP",
              ctx.getChannel(),
              ctx.getBizType()));
      ctx.setErrorResult(MessageResult.fail(
          ctx.getChannel(),
          "消息重复,已忽略",
          MessageExceptionCode.MESSAGE_DUPLICATED.getCode()));
      return false;
    }
    return true;
  }

  @Override
  public int order() {
    return 600;
  }

  /**
   * 构建去重 key：bizId + receiver + templateCode，含 channel 时追加 channel 维度。
   *
   * <p>格式：
   * <ul>
   *   <li>有 channel：ydsz:msg:dedup:{bizId}:{receiver}:{templateCode}:{channel}</li>
   *   <li>无 channel：ydsz:msg:dedup:{bizId}:{receiver}:{templateCode}</li>
   * </ul>
   */
  private String buildDedupKey(MessageRequest request) {
    if (!StringUtils.hasText(request.getBizId())) {
      return null;
    }
    String receiver = StringUtils.hasText(request.getReceiver()) ? request.getReceiver() : "";
    String templateCode =
        StringUtils.hasText(request.getTemplateCode()) ? request.getTemplateCode() : "";
    StringBuilder key = new StringBuilder(MessageConstants.DEDUP_KEY_PREFIX)
        .append(request.getBizId()).append(":")
        .append(receiver).append(":")
        .append(templateCode);
    if (StringUtils.hasText(request.getChannel())) {
      key.append(":").append(request.getChannel());
    }
    return key.toString();
  }
}
