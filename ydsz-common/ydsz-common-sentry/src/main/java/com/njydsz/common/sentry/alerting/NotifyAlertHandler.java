package com.njydsz.common.sentry.alerting;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.njydsz.common.notify.core.NotifyService;
import com.njydsz.common.notify.enums.NotifyChannel;
import com.njydsz.common.sentry.domain.AlertEvent;
import com.njydsz.common.sentry.domain.AlertSeverity;

/**
 * 告警通知处理器
 *
 * <p>将 {@link AlertEvent} 转换为通知消息，通过 {@link NotifyService} 发送到对应渠道。 根据告警级别路由：
 *
 * <ul>
 *   <li>P0 - 钉钉（立即通知）
 *   <li>P1 - 钉钉（5 分钟聚合由 AlertConverger 控制）
 *   <li>P2 - 邮件
 *   <li>P3 - 仅记录，不通知
 * </ul>
 *
 * <p>当 {@code common-notify} 模块不可用时，此 Handler 不会被注册。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@RequiredArgsConstructor
public class NotifyAlertHandler implements DefaultAlertPublisher.AlertHandler {

  private final NotifyService notifyService;
  private final String dingtalkReceiver;
  private final String emailReceiver;

  @Override
  public void handle(AlertEvent event) {
    if (event.getSeverity() == AlertSeverity.P3) {
      return;
    }

    NotifyChannel channel = resolveChannel(event.getSeverity());
    String receiver = resolveReceiver(event.getSeverity());
    String title = buildTitle(event);
    String content = buildContent(event);

    try {
      notifyService.send(channel, receiver, title, content);
    } catch (Exception e) {
      log.warn(
          "[Sentry] 告警通知发送失败: severity={}, name={}, err={}",
          event.getSeverity(),
          event.getName(),
          e.getMessage());
    }
  }

  private NotifyChannel resolveChannel(AlertSeverity severity) {
    return switch (severity) {
      case P0, P1 -> NotifyChannel.DINGTALK;
      case P2 -> NotifyChannel.EMAIL;
      case P3 -> NotifyChannel.DINGTALK; // unreachable: P3 filtered in handle()
    };
  }

  private String resolveReceiver(AlertSeverity severity) {
    return switch (severity) {
      case P0, P1 -> dingtalkReceiver;
      case P2 -> emailReceiver;
      case P3 -> dingtalkReceiver; // unreachable: P3 filtered in handle()
    };
  }

  private String buildTitle(AlertEvent event) {
    return String.format(
        "[YDSZ-P%s] %s",
        event.getSeverity() != null ? event.getSeverity().name() : "UNKNOWN",
        event.getName() != null ? event.getName() : "Unknown Alert");
  }

  private String buildContent(AlertEvent event) {
    StringBuilder sb = new StringBuilder(256);
    sb.append("### ").append(buildTitle(event)).append("\n\n");
    sb.append("| 维度 | 值 |\n|---|---|\n");
    sb.append("| **级别** | P").append(event.getSeverity()).append(" |\n");
    sb.append("| **摘要** | ")
        .append(event.getSummary() != null ? event.getSummary() : "")
        .append(" |\n");
    sb.append("| **详情** | ")
        .append(event.getDescription() != null ? event.getDescription() : "")
        .append(" |\n");
    sb.append("| **分类** | ")
        .append(event.getCategory() != null ? event.getCategory() : "")
        .append(" |\n");
    sb.append("| **触发值** | ").append(event.getValue()).append(" |\n");
    if (event.getRunbookUrl() != null) {
      sb.append("| **Runbook** | ").append(event.getRunbookUrl()).append(" |\n");
    }
    return sb.toString();
  }
}
