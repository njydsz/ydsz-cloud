package com.njydsz.message.server.service.chain.handler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.njydsz.common.feign.MessageRequest;
import com.njydsz.common.feign.MessageResult;
import com.njydsz.common.safe.sensitive.SensitiveUtil;
import com.njydsz.message.domain.enums.MessageExceptionCode;
import com.njydsz.message.server.metric.MessageMetrics;
import com.njydsz.message.server.service.chain.SendContext;
import com.njydsz.message.server.service.chain.SendHandler;
import com.njydsz.message.server.service.impl.ChannelSuppressionEngine;

/**
 * 跨渠道抑制 Handler。
 *
 * <p>同一业务对象（bizType + bizId）在短时间内已通过其他渠道发送给用户时， 抑制当前通道的发送，避免多渠道重复打扰。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
@Order(700)
@RequiredArgsConstructor
public class SuppressionHandler implements SendHandler {
  /** 抑制处理器优先级 */
  private static final int SUPPRESSION_PRIORITY = 700;


  private final ChannelSuppressionEngine channelSuppressionEngine;
  private final MessageMetrics messageMetrics;

  @Override
  public boolean handle(MessageRequest request, SendContext ctx) {
    String bizType = ctx.getBizType();
    String bizId = request.getBizId();
    String receiver = ctx.getReceiver();
    String channel = ctx.getChannel();
    if (!StringUtils.hasText(bizType)
        || !StringUtils.hasText(bizId)
        || !StringUtils.hasText(receiver)) {
      return true;
    }
    if (channelSuppressionEngine.shouldSuppress(bizType, bizId, receiver, channel)) {
      log.info(
          "[Message] 跨渠道抑制,跳过发送: bizType={} bizId={} receiver={} channel={}",
          bizType,
          bizId,
          SensitiveUtil.scanAndMask(receiver),
          channel);
      messageMetrics.recordSend(channel, "SUPPRESSED", 0);
      ctx.setErrorResult(MessageResult.fail(
          channel,
          "跨渠道抑制: 已有其他渠道发送",
          MessageExceptionCode.CHANNEL_SUPPRESSED.getCode()));
      return false;
    }
    return true;
  }

  @Override
  public int order() {
    return SUPPRESSION_PRIORITY;
  }
}
