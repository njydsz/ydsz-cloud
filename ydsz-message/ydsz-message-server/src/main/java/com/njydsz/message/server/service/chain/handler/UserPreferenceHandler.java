package com.njydsz.message.server.service.chain.handler;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.njydsz.common.feign.MessageRequest;
import com.njydsz.common.feign.MessageResult;
import com.njydsz.common.safe.sensitive.SensitiveUtil;
import com.njydsz.message.domain.enums.MessageExceptionCode;
import com.njydsz.message.domain.vo.MsgPreferenceVO;
import com.njydsz.message.server.config.MessageProperties;
import com.njydsz.message.server.metric.MessageMetrics;
import com.njydsz.message.server.service.chain.SendContext;
import com.njydsz.message.server.service.chain.SendHandler;
import com.njydsz.message.server.service.config.PreferenceService;
import com.njydsz.message.server.service.config.SubscriptionService;
import com.njydsz.message.server.service.impl.DndService;

/**
 * 用户偏好校验 Handler（订阅关系 + 免打扰时段）。
 *
 * <p>合并原 SubscriptionHandler 与 PreferenceHandler，执行两步操作：
 *
 * <ol>
 *   <li>校验用户是否已退订该消息主题，已退订时短路管线</li>
 *   <li>检查用户是否设置了免打扰时段（DND），在 DND 时段内按策略处理</li>
 * </ol>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
@Order(400)
@RequiredArgsConstructor
public class UserPreferenceHandler implements SendHandler {
  /** 每小时秒数 */
  private static final int SECONDS_PER_HOUR = 3600;

  /** 偏好处理器优先级 */
  private static final int PREFERENCE_PRIORITY = 400;


  private final SubscriptionService subscriptionService;
  private final PreferenceService preferenceService;
  private final MessageProperties messageProperties;
  private final MessageMetrics messageMetrics;

  @Override
  public boolean handle(MessageRequest request, SendContext ctx) {
    String receiver = ctx.getReceiver();
    String templateCode = ctx.getTemplateCode();
    String channel = ctx.getChannel();
    // 1. 订阅关系校验
    if (StringUtils.hasText(receiver) && StringUtils.hasText(templateCode)) {
      if (subscriptionService.isBlocked(receiver, templateCode, channel)) {
        log.info(
            "[Message] 用户已退订,跳过发送: receiver={} topic={} channel={}",
            SensitiveUtil.scanAndMask(receiver),
            templateCode,
            channel);
        messageMetrics.recordSend(channel, "BLOCKED", 0);
        ctx.setErrorResult(MessageResult.fail(
            channel,
            MessageExceptionCode.USER_UNSUBSCRIBED.getCode(),
            "用户已退订该消息",
            "用户已退订该消息",
            null));
        return false;
      }
    }
    // 2. 免打扰时段校验
    if (!StringUtils.hasText(receiver)) {
      return true;
    }
    String bizType = ctx.getBizType();
    MsgPreferenceVO pref = preferenceService.getByUser(receiver, channel, bizType);
    ctx.setPreference(pref);
    if (pref == null || !Integer.valueOf(1).equals(pref.getDndEnabled())) {
      return true;
    }
    if (!isInDndPeriod(pref)) {
      return true;
    }
    // 在 DND 时段内
    MessageProperties.SmartTimingConfig stc = messageProperties.getSmartTiming();
    boolean channelDisruptive = stc != null && stc.isDisruptive(channel);
    boolean urgentBypass =
        stc != null && stc.isUrgentBypassDnd() && "URGENT".equals(resolvePriority(request));
    if (!channelDisruptive) {
      log.debug("[Message] 非打扰型通道绕过 DND: channel={}", channel);
      return true;
    }
    if (urgentBypass) {
      log.info(
          "[Message] URGENT 消息绕过 DND: receiver={} channel={}",
          SensitiveUtil.scanAndMask(receiver),
          channel);
      return true;
    }
    if (stc != null && stc.isEnabled()) {
      return handleSmartTiming(request, ctx, pref, stc, channel, receiver);
    }
    messageMetrics.recordSend(channel, "DND_SKIPPED", 0);
    ctx.setErrorResult(MessageResult.fail(
        channel,
        MessageExceptionCode.DND_PERIOD_ACTIVE.getCode(),
        "当前为免打扰时段",
        "当前为免打扰时段",
        null));
    return false;
  }

  @Override
  public int order() {
    return PREFERENCE_PRIORITY;
  }

  /**
   * 智能定时处理：延迟到 DND 结束后发送。
   *
     * @param request 消息发送请求
   * @param ctx 管线上下文
   * @param pref 用户偏好设置
   * @param stc 智能定时配置
   * @param channel 消息通道标识
   * @param receiver 接收方标识
   * @return true 表示继续管线，false 表示中断（延迟到定时后发送）
   */
  private boolean handleSmartTiming(
      MessageRequest request,
      SendContext ctx,
      MsgPreferenceVO pref,
      MessageProperties.SmartTimingConfig stc,
      String channel,
      String receiver) {
    LocalTime start = parseTime(pref.getDndStart());
    LocalTime end = parseTime(pref.getDndEnd());
    if (start == null || end == null) {
      messageMetrics.recordSend(channel, "DND_SKIPPED", 0);
      ctx.setErrorResult(MessageResult.fail(channel, null, "当前为免打扰时段", "当前为免打扰时段", null));
      return false;
    }
    LocalDateTime now = LocalDateTime.now();
    LocalDateTime windowEnd = DndService.resolveWindowEnd(now, start, end);
    long buffer = stc.getDndBufferSeconds();
    LocalDateTime nextTime = windowEnd.plusSeconds(buffer);
    long deferSeconds = Duration.between(now, nextTime).getSeconds();
    long maxDeferSeconds = stc.getMaxDeferHours() * SECONDS_PER_HOUR;
    if (deferSeconds > maxDeferSeconds) {
      log.info(
          "[Message] DND 延迟超过阈值,丢弃: receiver={} defer={}s max={}s",
          SensitiveUtil.scanAndMask(receiver),
          deferSeconds,
          maxDeferSeconds);
      messageMetrics.recordSend(channel, "DND_DROPPED", 0);
      ctx.setErrorResult(MessageResult.fail(
          channel,
          MessageExceptionCode.DND_DEFER_EXCEED.getCode(),
          "免打扰时段消息延迟过久,已丢弃",
          "免打扰时段消息延迟过久,已丢弃",
          null));
      return false;
    }
    log.info(
        "[Message] DND 延迟发送: receiver={} dnd={}~{} nextSendAt={}",
        SensitiveUtil.scanAndMask(receiver),
        pref.getDndStart(),
        pref.getDndEnd(),
        nextTime);
    messageMetrics.recordSend(channel, "DND_DEFERRED", 0);
    request.setScheduledAt(nextTime);
    ctx.setScheduledAt(nextTime);
    return true;
  }

  /**
   * 判断当前是否在 DND 免打扰时段。
   * 
   * <p>复用 {@link DndService#isInWindow} 实现，消除重复的跨天窗口判断逻辑。
   *
   * @param pref 用户的偏好设置（含 DND 时段配置）
   * @return 当前时间处于 DND 时段内时返回 true
    String start = pref.getDndStart();
    String end = pref.getDndEnd();
    if (!StringUtils.hasText(start) || !StringUtils.hasText(end)) {
      return false;
    }
    try {
      LocalTime s = LocalTime.parse(start);
      LocalTime e = LocalTime.parse(end);
      return DndService.isInWindow(LocalTime.now(), s, e);
    } catch (Exception ex) {
      log.warn("[Message] DND 时段解析失败: start={} end={} err={}", start, end, ex.getMessage());
      return false;
    }
  }

  /**
   * 安全解析时间字符串。
   *
   * @param value 时间字符串（HH:mm 格式）
   * @return 解析成功返回 LocalTime，失败返回 null
    if (!StringUtils.hasText(value)) {
      return null;
    }
    try {
      return LocalTime.parse(value);
    } catch (Exception e) {
      return null;
    }
  }

  /**
   * 解析消息优先级。
   *
   * @param request 消息发送请求
   * @return 优先级字符串（如 NORMAL、URGENT）
    return request.getPriority() != null ? request.getPriority() : "NORMAL";
  }
}
