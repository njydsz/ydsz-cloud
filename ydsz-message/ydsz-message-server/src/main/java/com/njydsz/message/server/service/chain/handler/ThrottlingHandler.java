package com.njydsz.message.server.service.chain.handler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.njydsz.common.core.constant.SystemConstants;
import com.njydsz.common.notify.enums.NotifyChannel;
import com.njydsz.common.notify.ratelimit.NotifyRateLimiterManager;
import com.njydsz.message.domain.dto.MessageItemRequestDTO;
import com.njydsz.message.domain.vo.MessageSendResultVO;
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
 * @since 26.09.01
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
  /**
   * notify 管道限流管理器（ObjectProvider 可选注入）。
   *
   * <p>common-notify 未装配时，仅走消息级限流，保持向后兼容。
   */
  private final ObjectProvider<NotifyRateLimiterManager> notifyRateLimiterProvider;

  @Override
  public boolean handle(MessageItemRequestDTO request, SendContext ctx) {
    String channel = ctx.getChannel();
    String bizType = ctx.getBizType();
    String receiver = ctx.getReceiver();
    String templateCode = ctx.getTemplateCode();
    // 1. 通道级 QPS 限流
    if (!guardService.tryAcquire(buildChannelLimitKey(channel, bizType), 1)) {
      messageMetrics.recordSend(channel, "FAILED", 0);
      ctx.setErrorResult(MessageSendResultVO.fail(
          channel,
          MessageExceptionCode.SEND_RATE_LIMITED.getCode(),
          "发送限流，请稍后重试",
          "发送限流，请稍后重试",
          null));
      return false;
    }
    // 2. 多维度限流校验
    if (!guardService.checkSendLimit(
        channel, receiver, templateCode, ctx.getTenantId(), request.getPriority())) {
      messageMetrics.recordSend(channel, "RATE_LIMITED", 0);
      ctx.setErrorResult(MessageSendResultVO.fail(
          channel,
          MessageExceptionCode.SEND_DIMENSION_LIMITED.getCode(),
          "多维度限流：receiver/template/tenant 超限",
          "多维度限流：receiver/template/tenant 超限",
          null));
      return false;
    }
    // 3. 用户频率校验
    if (StringUtils.hasText(receiver)
        && !guardService.checkFrequency(receiver, channel, bizType)) {
      messageMetrics.recordSend(channel, "FAILED", 0);
      ctx.setErrorResult(MessageSendResultVO.fail(
          channel,
          MessageExceptionCode.SEND_FREQUENCY_LIMITED.getCode(),
          "发送频率超限",
          "发送频率超限",
          null));
      return false;
    }
    // 4. 发送方配额校验
    String senderId =
        (bizType != null && !bizType.isEmpty())
            ? bizType
            : SystemConstants.SYSTEM_USER_ID;
    if (!senderQuotaService.checkQuota(senderId, channel)) {
      messageMetrics.recordSend(channel, "QUOTA_EXCEEDED", 0);
      ctx.setErrorResult(MessageSendResultVO.fail(
          channel,
          MessageExceptionCode.SEND_QUOTA_EXHAUSTED.getCode(),
          "发送方配额已用尽: senderId=" + senderId,
          "发送方配额已用尽: senderId=" + senderId,
          null));
      return false;
    }
    // 双保险：消息级限流全部通过后，再委托 notify 管道做通道级限流
    if (!checkNotifyRateLimit(channel, ctx)) {
      return false;
    }
    return true;
  }

  /**
   * 通知级通道限流检查（双保险第二层）。
   *
   * <p>消息级限流全部通过后，再使用 notify 的 {@link NotifyRateLimiterManager} 做一次通道级限流。
   * 若 notify 管道未装配或通道无对应枚举值，跳过；notify 调用异常时降级到纯 message 管道。
   *
   * @param channel 通道标识（如 EMAIL/SMS/DINGTALK）
   * @param ctx     管线上下文
   * @return true 表示允许继续，false 表示被 notify 限流拦截
   */
  private boolean checkNotifyRateLimit(String channel, SendContext ctx) {
    NotifyRateLimiterManager rateLimiterManager = notifyRateLimiterProvider.getIfAvailable();
    if (rateLimiterManager == null) {
      return true;
    }
    NotifyChannel notifyChannel = resolveNotifyChannel(channel);
    if (notifyChannel == null) {
      return true;
    }
    try {
      if (!rateLimiterManager.tryAcquire(notifyChannel, ctx.getTenantId())) {
        log.warn(
            "[ThrottlingHandler] notify 管道通道限流触发: channel={}, tenantId={}",
            channel,
            ctx.getTenantId());
        messageMetrics.recordSend(channel, "NOTIFY_RATE_LIMITED", 0);
        ctx.setErrorResult(MessageSendResultVO.fail(
            channel,
            MessageExceptionCode.SEND_RATE_LIMITED.getCode(),
            "发送限流，请稍后重试",
            "发送限流，请稍后重试",
            null));
        return false;
      }
    } catch (Exception e) {
      log.warn(
          "[ThrottlingHandler] notify 管道限流异常,降级到纯 message 管道: channel={}, err={}",
          channel,
          e.getMessage());
    }
    return true;
  }

  /**
   * 将消息通道字符串解析为 {@link NotifyChannel} 枚举。
   *
   * <p>仅处理 notify 有对应枚举值的通道（EMAIL/SMS/DINGTALK/WECOM/FEISHU/INAPP），
   * 其他通道（PUSH/WEBHOOK 等）返回 null，由调用方跳过 notify 级限流。
   *
   * @param channel 消息通道字符串
   * @return 对应的 NotifyChannel 枚举，无对应值时返回 null
   */
  private NotifyChannel resolveNotifyChannel(String channel) {
    if (!StringUtils.hasText(channel)) {
      return null;
    }
    switch (channel.trim().toUpperCase()) {
      case "EMAIL":
        return NotifyChannel.EMAIL;
      case "SMS":
        return NotifyChannel.SMS;
      case "DINGTALK":
      case "DINGTALK_WORK":
        return NotifyChannel.DINGTALK;
      case "WECOM":
      case "WECOM_APP":
        return NotifyChannel.WECOM;
      case "FEISHU":
        return NotifyChannel.FEISHU;
      case "INAPP":
        return NotifyChannel.INSITE;
      default:
        return null;
    }
  }

  @Override
  public int order() {
    return THROTTLING_PRIORITY;
  }

  /**
   * 构建通道级限流 key：通道 + 业务类型。
   *
   * @param channel 通道标识（如 SMS、EMAIL）
   * @param bizType 业务类型（如 order、notify）
   * @return 限流 key 字符串
   */
  private String buildChannelLimitKey(String channel, String bizType) {
    return "channel:"
        + (channel != null ? channel : "UNKNOWN")
        + ":biz:"
        + (bizType != null ? bizType : "DEFAULT");
  }
}
