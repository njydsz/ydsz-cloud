package com.njydsz.message.server.service.chain.handler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.njydsz.common.core.code.BaseResultCode;
import com.njydsz.common.exception.custom.SysException;
import com.njydsz.common.feign.MessageRequest;
import com.njydsz.message.server.metric.MessageMetrics;
import com.njydsz.message.server.service.chain.SendContext;
import com.njydsz.message.server.service.chain.SendHandler;
import com.njydsz.message.server.service.core.RateLimitService;

/**
 * 限流与频率校验 Handler。
 *
 * <p>多维度限流：通道级 QPS + 接收人频率 + 模板频率 + 租户配额。 限流触发时抛 {@link SysException}（TOO_MANY_REQUESTS），由上层统一处理。
 *
 * @author ydsz-team
 * @since 1.1.0
 */
@Slf4j
@Component
@Order(800)
@RequiredArgsConstructor
public class RateLimitHandler implements SendHandler {

  private final RateLimitService rateLimitService;
  private final MessageMetrics messageMetrics;

  @Override
  public boolean handle(MessageRequest request, SendContext ctx) {
    String channel = ctx.getChannel();
    String bizType = ctx.getBizType();
    String receiver = ctx.getReceiver();
    String templateCode = ctx.getTemplateCode();
    // 通道级 QPS 限流
    if (!rateLimitService.tryAcquire(buildRateLimitKey(channel, bizType), 1)) {
      messageMetrics.recordSend(channel, "FAILED", 0);
      throw SysException.builder()
          .resultCode(BaseResultCode.TOO_MANY_REQUESTS)
          .message("发送限流，请稍后重试")
          .build();
    }
    // 多维度限流校验
    if (!rateLimitService.checkSendLimit(
        channel, receiver, templateCode, ctx.getTenantId(), request.getPriority())) {
      messageMetrics.recordSend(channel, "RATE_LIMITED", 0);
      throw SysException.builder()
          .resultCode(BaseResultCode.TOO_MANY_REQUESTS)
          .message("多维度限流：receiver/template/tenant 超限")
          .build();
    }
    // 用户频率校验
    if (StringUtils.hasText(receiver)
        && !rateLimitService.checkFrequency(receiver, channel, bizType)) {
      messageMetrics.recordSend(channel, "FAILED", 0);
      throw SysException.builder()
          .resultCode(BaseResultCode.TOO_MANY_REQUESTS)
          .message("发送频率超限")
          .build();
    }
    return true;
  }

  @Override
  public int order() {
    return 800;
  }

  /** 构建限流 key：通道 + 业务类型。 */
  private String buildRateLimitKey(String channel, String bizType) {
    return "channel:"
        + (channel != null ? channel : "UNKNOWN")
        + ":biz:"
        + (bizType != null ? bizType : "DEFAULT");
  }
}
