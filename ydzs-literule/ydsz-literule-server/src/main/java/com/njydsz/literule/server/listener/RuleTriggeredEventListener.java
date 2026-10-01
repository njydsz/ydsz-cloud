package com.njydsz.literule.server.listener;

import java.util.Map;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import com.njydsz.common.notify.helper.NotifyHelper;
import com.njydsz.literule.server.spi.DefaultAlertActionHandler;

/**
 * 规则触发事件监听器 — 消费 {@link DefaultAlertActionHandler.RuleTriggeredEvent} 并发送通知。
 *
 * <p>由 {@link com.njydsz.literule.server.spi.DefaultAlertActionHandler} 在规则命中时发布本地 Spring 事件，
 * 本监听器异步消费该事件，根据 severity 级别选择发送方式：
 *
 * <ul>
 *   <li>{@code RED}（对应 ERROR/CRITICAL）→ {@link NotifyHelper#sendSystemAlert}（站内信 + 邮件双发）
 *   <li>{@code INFO} / {@code YELLOW}（对应 INFO/WARN）→ {@link NotifyHelper#sendInApp}（站内信）
 * </ul>
 *
 * <p>使用 {@code @Async} 异步消费，{@code @EventListener} 仅限本地 JVM 通知，不跨节点广播。
 *
 * @author ydsz-team
 * @since 26.09.30
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RuleTriggeredEventListener {

  /** 系统级默认接收者（规则事件无明确接收者时使用） */
  private static final String DEFAULT_RECEIVER = "system_admin";

  private final NotifyHelper notifyHelper;

  /**
   * 消费 {@link DefaultAlertActionHandler.RuleTriggeredEvent}，按 severity 发送通知。
   *
   * <p>异步消费，异常被隔离捕获，绝不反向影响规则引擎主链路。
   *
   * @param event 规则触发事件
   */
  @Async
  @EventListener
  public void onRuleTriggered(DefaultAlertActionHandler.RuleTriggeredEvent event) {
    if (event == null) {
      log.warn("[LiteRule-Listener] 收到 null 事件，忽略");
      return;
    }

    String severity = event.getSeverity();
    String title = event.getTitle();
    String content = buildContent(event);

    try {
      if ("RED".equalsIgnoreCase(severity)) {
        // RED 级别：系统告警，站内信 + 邮件双发
        notifyHelper.sendSystemAlert(title, content, DEFAULT_RECEIVER);
        log.info(
            "[LiteRule-Listener] RED 告警已发送: ruleCode={}, title={}",
            event.getRuleCode(),
            title);
      } else {
        // INFO / YELLOW 级别：站内信通知
        notifyHelper.sendInApp(DEFAULT_RECEIVER, title, content);
        log.info(
            "[LiteRule-Listener] {}/WARN 站内信已发送: ruleCode={}, title={}",
            severity,
            event.getRuleCode(),
            title);
      }
    } catch (Exception e) {
      log.warn(
          "[LiteRule-Listener] 规则触发通知发送异常: ruleCode={}, severity={}, error={}",
          event.getRuleCode(),
          severity,
          e.getMessage(),
          e);
    }
  }

  /**
   * 构建通知正文：拼接 description + facts 摘要。
   *
   * @param event 规则触发事件
   * @return 通知正文
   */
  private String buildContent(DefaultAlertActionHandler.RuleTriggeredEvent event) {
    StringBuilder sb = new StringBuilder();
    if (event.getDescription() != null && !event.getDescription().isBlank()) {
      sb.append(event.getDescription());
    }
    Map<String, Object> facts = event.getFacts();
    if (facts != null && !facts.isEmpty()) {
      if (!sb.isEmpty()) {
        sb.append("\n");
      }
      sb.append("事实数据：").append(facts);
    }
    if (sb.isEmpty()) {
      sb.append("规则 [").append(event.getRuleName()).append("] 已触发");
    }
    return sb.toString();
  }
}
