package com.njydsz.message.server.service.chain.handler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.njydsz.common.feign.MessageRequest;
import com.njydsz.common.feign.MessageResult;
import com.njydsz.common.safe.sensitive.SensitiveUtil;
import com.njydsz.message.server.metric.MessageMetrics;
import com.njydsz.message.server.service.chain.SendContext;
import com.njydsz.message.server.service.chain.SendHandler;
import com.njydsz.message.server.service.config.SubscriptionService;

/**
 * 订阅关系校验 Handler。
 *
 * <p>校验用户是否已退订该消息主题，已退订时短路管线。
 *
 * @author ydsz-team
 * @since 1.1.0
 */
@Slf4j
@Component
@Order(400)
@RequiredArgsConstructor
public class SubscriptionHandler implements SendHandler {

  private final SubscriptionService subscriptionService;
  private final MessageMetrics messageMetrics;

  @Override
  public boolean handle(MessageRequest request, SendContext ctx) {
    String receiver = ctx.getReceiver();
    String templateCode = ctx.getTemplateCode();
    String channel = ctx.getChannel();
    if (!StringUtils.hasText(receiver) || !StringUtils.hasText(templateCode)) {
      return true;
    }
    if (subscriptionService.isBlocked(receiver, templateCode, channel)) {
      log.info(
          "[Message] 用户已退订,跳过发送: receiver={} topic={} channel={}",
          SensitiveUtil.scanAndMask(receiver),
          templateCode,
          channel);
      messageMetrics.recordSend(channel, "BLOCKED", 0);
      ctx.setErrorResult(MessageResult.fail(channel, "用户已退订该消息"));
      return false;
    }
    return true;
  }

  @Override
  public int order() {
    return 400;
  }
}
