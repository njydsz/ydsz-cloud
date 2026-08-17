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
import com.njydsz.message.domain.event.MessageSkippedEvent;
import com.njydsz.message.server.event.DomainEventPublisher;
import com.njydsz.message.server.metric.MessageMetrics;
import com.njydsz.message.server.service.chain.SendContext;
import com.njydsz.message.server.service.chain.SendHandler;
import com.njydsz.message.server.service.core.GuardService;

/**
 * 智能去重 Handler。
 *
 * <p>使用 Redis SET NX EX 原子去重，窗口内重复消息跳过发送。 去重 key 由 bizId + receiver + templateCode 拼接而成。
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
          "[Message] 检测到重复消息,跳过发送: dedupKey={} receiver={}",
          dedupKey,
          SensitiveUtil.scanAndMask(ctx.getReceiver()));
      messageMetrics.recordSend(ctx.getChannel(), "DEDUPED", 0);
      // P2-A4: 发布消息被拦截领域事件
      domainEventPublisher.publish(
          new MessageSkippedEvent(
              TenantContextHolder.getTenantId(),
              request.getMessageId(),
              "DEDUP",
              ctx.getChannel(),
              ctx.getBizType()));
      ctx.setErrorResult(MessageResult.fail(ctx.getChannel(), "消息重复,已忽略"));
      return false;
    }
    return true;
  }

  @Override
  public int order() {
    return 600;
  }

  /** 构建去重 key：bizId + receiver + templateCode。 */
  private String buildDedupKey(MessageRequest request) {
    if (!StringUtils.hasText(request.getBizId())) {
      return null;
    }
    String receiver = StringUtils.hasText(request.getReceiver()) ? request.getReceiver() : "";
    String templateCode =
        StringUtils.hasText(request.getTemplateCode()) ? request.getTemplateCode() : "";
    return MessageConstants.DEDUP_KEY_PREFIX
        + request.getBizId()
        + ":"
        + receiver
        + ":"
        + templateCode;
  }
}
