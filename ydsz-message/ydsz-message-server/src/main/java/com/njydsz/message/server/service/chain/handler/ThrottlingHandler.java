package com.njydsz.message.server.service.chain.handler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.njydsz.common.core.code.BaseResultCode;
import com.njydsz.common.core.constant.SystemConstants;
import com.njydsz.common.exception.custom.SysException;
import com.njydsz.common.feign.MessageRequest;
import com.njydsz.message.server.metric.MessageMetrics;
import com.njydsz.message.server.service.chain.SendContext;
import com.njydsz.message.server.service.chain.SendHandler;
import com.njydsz.message.server.service.core.GuardService;
import com.njydsz.message.server.service.impl.SenderQuotaService;

/**
 * 流量控制 Handler（通道限流 + 发送方配额）。
 *
 * <p>合并原 RateLimitHandler 与 QuotaHandler，执行两步操作：
 *
 * <ol>
 *   <li>多维度限流：通道级 QPS + 接收人频率 + 模板频率 + 租户配额</li>
 *   <li>发送方配额：bizType 级日/月总量控制</li>
 * </ol>
 *
 * <p>限流触发时抛 {@link SysException}（TOO_MANY_REQUESTS），由上层统一处理。
 *
 * @author ydsz-team
 * @since 1.2.0
 */
@Slf4j
@Component
@Order(800)
@RequiredArgsConstructor
public class ThrottlingHandler implements SendHandler {

  private final GuardService guardService;
  private final SenderQuotaService senderQuotaService;
  private final MessageMetrics messageMetrics;

  @Override
  public boolean handle(MessageRequest request, SendContext ctx) {
    String channel = ctx.getChannel();
    String bizType = ctx.getBizType();
    String receiver = ctx.getReceiver();
    String templateCode = ctx.getTemplateCode();
    // 1. 通道级 QPS 限流
    if (!guardService.tryAcquire(buildChannelLimitKey(channel, bizType), 1)) {
      messageMetrics.recordSend(channel, "FAILED", 0);
      throw SysException.builder()
          .resultCode(BaseResultCode.TOO_MANY_REQUESTS)
          .message("发送限流，请稍后重试")
          .build();
    }
    // 2. 多维度限流校验
    if (!guardService.checkSendLimit(
        channel, receiver, templateCode, ctx.getTenantId(), request.getPriority())) {
      messageMetrics.recordSend(channel, "RATE_LIMITED", 0);
      throw SysException.builder()
          .resultCode(BaseResultCode.TOO_MANY_REQUESTS)
          .message("多维度限流：receiver/template/tenant 超限")
          .build();
    }
    // 3. 用户频率校验
    if (StringUtils.hasText(receiver)
        && !guardService.checkFrequency(receiver, channel, bizType)) {
      messageMetrics.recordSend(channel, "FAILED", 0);
      throw SysException.builder()
          .resultCode(BaseResultCode.TOO_MANY_REQUESTS)
          .message("发送频率超限")
          .build();
    }
    // 4. 发送方配额校验
    String senderId =
        (bizType != null && !bizType.isEmpty())
            ? bizType
            : SystemConstants.SYSTEM_USER_ID;
    if (!senderQuotaService.checkQuota(senderId, channel)) {
      messageMetrics.recordSend(channel, "QUOTA_EXCEEDED", 0);
      throw SysException.builder()
          .resultCode(BaseResultCode.TOO_MANY_REQUESTS)
          .message("发送方配额已用尽: senderId=" + senderId)
          .build();
    }
    return true;
  }

  @Override
  public int order() {
    return 800;
  }

  /** 构建通道级限流 key：通道 + 业务类型。 */
  private String buildChannelLimitKey(String channel, String bizType) {
    return "channel:"
        + (channel != null ? channel : "UNKNOWN")
        + ":biz:"
        + (bizType != null ? bizType : "DEFAULT");
  }
}
