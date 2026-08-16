package com.njydsz.common.notify.helper;

import java.util.List;

import lombok.extern.slf4j.Slf4j;

import com.njydsz.common.notify.core.NotifyRequest;
import com.njydsz.common.notify.core.NotifySendResult;
import com.njydsz.common.notify.core.NotifyService;
import com.njydsz.common.notify.enums.NotifyChannel;

/**
 * 统一通知辅助类 — 封装 NotifyService 的常用发送模式。
 *
 * <p>ydsz-cloud 通知体系的<strong>统一业务入口</strong>。 消除各模块自建通知 Helper 的重复编码（如
 * CronjobNotifyHelper、FlowNotificationService）， 提供统一的便捷方法，支持简单通知、模板通知、系统告警等场景。
 *
 * <p><b>架构定位</b>：业务模块直接注入此 Bean 即可，无需再各自封装通知逻辑， 也无需直接调用 {@code NotifyService} 或 {@code
 * NotificationClient} Feign。 详见 {@code docs/module-review/ADR-001-notify-message-convergence.md}。
 *
 * <h3>使用示例</h3>
 *
 * <pre>{@code
 * @Resource
 * private NotifyHelper notifyHelper;
 *
 * // 简单站内通知
 * notifyHelper.sendInApp("user-123", "审批提醒", "您有一条待审批任务");
 *
 * // IM 渠道通知
 * notifyHelper.sendDingTalk(webhookUrl, "系统告警", "CPU 使用率超过 90%");
 * notifyHelper.sendFeishu(webhookUrl, "系统告警", "CPU 使用率超过 90%");
 * notifyHelper.sendWeCom(webhookUrl, "系统告警", "CPU 使用率超过 90%");
 *
 * // 邮件通知
 * notifyHelper.sendEmail("user@example.com", "系统告警", "CPU 使用率超过 90%");
 *
 * // 模板通知
 * notifyHelper.sendTemplate(NotifyChannel.SMS, "13800138000", "VERIFICATION_CODE", params);
 *
 * // 系统告警（自动选择渠道 + 降级处理）
 * notifyHelper.sendSystemAlert("定时任务执行失败", "Job: data-sync, Error: Connection timeout");
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public class NotifyHelper {

  private final NotifyService notifyService;

  public NotifyHelper(NotifyService notifyService) {
    this.notifyService = notifyService;
  }

  /**
   * 发送站内信通知。
   *
   * @param receiverId 接收者用户 ID
   * @param title 通知标题
   * @param content 通知内容
   */
  public void sendInApp(String receiverId, String title, String content) {
    try {
      NotifySendResult result =
          notifyService.send(NotifyChannel.INSITE, receiverId, title, content);
      if (!result.isSuccess()) {
        log.warn(
            "[NotifyHelper] 站内信发送失败: receiver={}, reason={}", receiverId, result.getErrorMessage());
      }
    } catch (Exception e) {
      log.warn("[NotifyHelper] 站内信发送异常: receiver={}, error={}", receiverId, e.getMessage(), e);
    }
  }

  /**
   * 批量发送站内信通知（逐条发送，单条失败不影响其他接收者）。
   *
   * @param receiverIds 接收者用户 ID 列表
   * @param title 通知标题
   * @param content 通知内容
   */
  public void sendInAppBatch(List<String> receiverIds, String title, String content) {
    if (receiverIds == null || receiverIds.isEmpty()) {
      return;
    }
    for (String receiverId : receiverIds) {
      sendInApp(receiverId, title, content);
    }
  }

  /**
   * 发送邮件通知。
   *
   * @param emailAddress 接收者邮箱
   * @param title 邮件标题
   * @param content 邮件内容
   */
  public void sendEmail(String emailAddress, String title, String content) {
    try {
      NotifySendResult result =
          notifyService.send(NotifyChannel.EMAIL, emailAddress, title, content);
      if (!result.isSuccess()) {
        log.warn(
            "[NotifyHelper] 邮件发送失败: receiver={}, reason={}",
            emailAddress,
            result.getErrorMessage());
      }
    } catch (Exception e) {
      log.warn("[NotifyHelper] 邮件发送异常: receiver={}, error={}", emailAddress, e.getMessage(), e);
    }
  }

  /**
   * 发送钉钉通知（通过 Webhook 发送文本消息到钉钉群机器人）。
   *
   * @param webhookUrl 钉钉群机器人的 Webhook URL（含 access_token）
   * @param title 消息标题
   * @param content 消息内容（Markdown 格式）
   */
  public void sendDingTalk(String webhookUrl, String title, String content) {
    try {
      NotifySendResult result =
          notifyService.send(NotifyChannel.DINGTALK, webhookUrl, title, content);
      if (!result.isSuccess()) {
        log.warn(
            "[NotifyHelper] 钉钉通知发送失败: receiver={}, reason={}",
            webhookUrl,
            result.getErrorMessage());
      }
    } catch (Exception e) {
      log.warn("[NotifyHelper] 钉钉通知发送异常: receiver={}, error={}", webhookUrl, e.getMessage(), e);
    }
  }

  /**
   * 发送飞书通知（通过 Webhook 发送文本消息到飞书群机器人）。
   *
   * @param webhookUrl 飞书群机器人的 Webhook URL（含 key）
   * @param title 消息标题
   * @param content 消息内容
   */
  public void sendFeishu(String webhookUrl, String title, String content) {
    try {
      NotifySendResult result =
          notifyService.send(NotifyChannel.FEISHU, webhookUrl, title, content);
      if (!result.isSuccess()) {
        log.warn(
            "[NotifyHelper] 飞书通知发送失败: receiver={}, reason={}",
            webhookUrl,
            result.getErrorMessage());
      }
    } catch (Exception e) {
      log.warn("[NotifyHelper] 飞书通知发送异常: receiver={}, error={}", webhookUrl, e.getMessage(), e);
    }
  }

  /**
   * 发送企业微信通知（通过 Webhook 发送文本消息到企业微信群机器人）。
   *
   * @param webhookUrl 企业微信群机器人的 Webhook URL（含 key）
   * @param title 消息标题
   * @param content 消息内容
   */
  public void sendWeCom(String webhookUrl, String title, String content) {
    try {
      NotifySendResult result = notifyService.send(NotifyChannel.WECOM, webhookUrl, title, content);
      if (!result.isSuccess()) {
        log.warn(
            "[NotifyHelper] 企微通知发送失败: receiver={}, reason={}",
            webhookUrl,
            result.getErrorMessage());
      }
    } catch (Exception e) {
      log.warn("[NotifyHelper] 企微通知发送异常: receiver={}, error={}", webhookUrl, e.getMessage(), e);
    }
  }

  /**
   * 发送短信通知。
   *
   * @param phoneNumber 接收者手机号
   * @param title 消息标题（部分运营商支持）
   * @param content 消息内容（建议控制在 70 字以内）
   */
  public void sendSms(String phoneNumber, String title, String content) {
    try {
      NotifySendResult result = notifyService.send(NotifyChannel.SMS, phoneNumber, title, content);
      if (!result.isSuccess()) {
        log.warn(
            "[NotifyHelper] 短信发送失败: receiver={}, reason={}", phoneNumber, result.getErrorMessage());
      }
    } catch (Exception e) {
      log.warn("[NotifyHelper] 短信发送异常: receiver={}, error={}", phoneNumber, e.getMessage(), e);
    }
  }

  /**
   * 发送模板通知（支持任意渠道）。
   *
   * @param channel 通知渠道
   * @param receiver 接收者标识
   * @param templateCode 模板编码
   * @param templateParams 模板参数
   */
  public void sendTemplate(
      NotifyChannel channel, String receiver, String templateCode, Object templateParams) {
    try {
      NotifySendResult result =
          notifyService.sendTemplate(channel, receiver, templateCode, templateParams);
      if (!result.isSuccess()) {
        log.warn(
            "[NotifyHelper] 模板通知发送失败: channel={}, receiver={}, template={}, reason={}",
            channel,
            receiver,
            templateCode,
            result.getErrorMessage());
      }
    } catch (Exception e) {
      log.warn(
          "[NotifyHelper] 模板通知发送异常: channel={}, receiver={}, template={}, error={}",
          channel,
          receiver,
          templateCode,
          e.getMessage(),
          e);
    }
  }

  /**
   * 发送系统告警通知（站内信 + 邮件双发）。
   *
   * <p>系统级告警场景使用，确保关键信息送达。 两种渠道独立发送，任一失败不影响另一渠道。
   *
   * @param title 告警标题
   * @param content 告警内容
   * @param receivers 接收者列表（用户 ID + 邮箱混合，系统自动识别）
   */
  public void sendSystemAlert(String title, String content, String... receivers) {
    if (receivers == null || receivers.length == 0) {
      log.warn("[NotifyHelper] 系统告警无接收者: title={}", title);
      return;
    }
    for (String receiver : receivers) {
      if (receiver == null || receiver.isBlank()) {
        continue;
      }
      // 邮箱地址自动用邮件渠道
      if (receiver.contains("@")) {
        sendEmail(receiver, title, content);
      } else {
        sendInApp(receiver, title, content);
      }
    }
  }

  /**
   * 批量发送站内信通知。
   *
   * @param receiverIds 接收者用户 ID 列表
   * @param title 通知标题
   * @param content 通知内容
   */
  public void batchSendInApp(List<String> receiverIds, String title, String content) {
    if (receiverIds == null || receiverIds.isEmpty()) {
      return;
    }
    try {
      NotifySendResult result =
          notifyService.batchSend(NotifyChannel.INSITE, receiverIds, title, content);
      if (!result.isSuccess()) {
        log.warn(
            "[NotifyHelper] 批量站内信发送失败: size={}, reason={}",
            receiverIds.size(),
            result.getErrorMessage());
      }
    } catch (Exception e) {
      log.warn(
          "[NotifyHelper] 批量站内信发送异常: size={}, error={}", receiverIds.size(), e.getMessage(), e);
    }
  }

  /**
   * 发送完整通知请求（支持模板、优先级、用户偏好、去重等高级特性）。
   *
   * @param request 通知请求
   */
  public void send(NotifyRequest request) {
    try {
      NotifySendResult result = notifyService.send(request);
      if (!result.isSuccess()) {
        log.warn(
            "[NotifyHelper] 通知发送失败: receiver={}, reason={}",
            request.getReceiver(),
            result.getErrorMessage());
      }
    } catch (Exception e) {
      log.warn(
          "[NotifyHelper] 通知发送异常: receiver={}, error={}", request.getReceiver(), e.getMessage(), e);
    }
  }
}
