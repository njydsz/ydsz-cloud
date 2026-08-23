package com.njydsz.message.server.service.chain.handler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.njydsz.common.core.constant.SystemConstants;
import com.njydsz.common.feign.MessageRequest;
import com.njydsz.common.feign.MessageResult;
import com.njydsz.message.domain.enums.MessageExceptionCode;
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
 * <p>限流触发时通过 {@link SendContext#setErrorResult} 设置带错误码的失败结果， 由管线统一短路。错误码使用 {@link MessageExceptionCode} 的 B915xx 段。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
@Order(800)
@RequiredArgsConstructor
public class ThrottlingHandler implements SendHandler {
  /** 限流处理器优先级 */
  private static final int THROTTLING_PRIORITY = 800;


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
      ctx.setErrorResult(MessageResult.fail(
          channel,
          "发送限流，请稍后重试",
          MessageExceptionCode.SEND_RATE_LIMITED.getCode()));
      return false;
    }
    // 2. 多维度限流校验
    if (!guardService.checkSendLimit(
        channel, receiver, templateCode, ctx.getTenantId(), request.getPriority())) {
      messageMetrics.recordSend(channel, "RATE_LIMITED", 0);
      ctx.setErrorResult(MessageResult.fail(
          channel,
          "多维度限流：receiver/template/tenant 超限",
          MessageExceptionCode.SEND_DIMENSION_LIMITED.getCode()));
      return false;
    }
    // 3. 用户频率校验
    if (StringUtils.hasText(receiver)
        && !guardService.checkFrequency(receiver, channel, bizType)) {
      messageMetrics.recordSend(channel, "FAILED", 0);
      ctx.setErrorResult(MessageResult.fail(
          channel,
          "发送频率超限",
          MessageExceptionCode.SEND_FREQUENCY_LIMITED.getCode()));
      return false;
    }
    // 4. 发送方配额校验
    String senderId =
        (bizType != null && !bizType.isEmpty())
            ? bizType
            : SystemConstants.SYSTEM_USER_ID;
    if (!senderQuotaService.checkQuota(senderId, channel)) {
      messageMetrics.recordSend(channel, "QUOTA_EXCEEDED", 0);
      ctx.setErrorResult(MessageResult.fail(
          channel,
          "发送方配额已用尽: senderId=" + senderId,
          MessageExceptionCode.SEND_QUOTA_EXHAUSTED.getCode()));
      return false;
    }
    return true;
  }

  @Override
  public int order() {
    return THROTTLING_PRIORITY;
  }

  /**
   * 构建通道级限流 key：通道 + 业务类型。
   *
   * @param channel 参数说明
   * @param bizType 参数说明
   * @return 返回值说明
   */
  private String buildChannelLimitKey(String channel, String bizType) {
    return "channel:"
        + (channel != null ? channel : "UNKNOWN")
        + ":biz:"
        + (bizType != null ? bizType : "DEFAULT");
  }
}
