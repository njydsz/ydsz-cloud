package com.njydsz.common.notify.audit;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.common.json.YdszJson;
import com.njydsz.common.notify.core.NotifySendResult;
import com.njydsz.common.notify.core.NotifyTraceContext;
import com.njydsz.common.notify.enums.NotifyChannel;

/**
 * 通知审计日志服务（P1-4）
 *
 * <p>记录每条通知发送的结构化审计日志，满足合规要求（如数据安全法、个人信息保护法）。 审计日志独立于业务日志，通过专用 Logger 输出，便于日志采集和合规审计。
 *
 * <p><b>审计字段：</b>
 *
 * <ul>
 *   <li>timestamp — 时间戳
 *   <li>traceId — 链路追踪ID
 *   <li>channel — 通知渠道
 *   <li>receiver_mask — 脱敏后的接收者标识
 *   <li>status — 发送状态（success/failure）
 *   <li>messageId — 消息ID（成功时）
 *   <li>error — 错误信息（失败时）
 *   <li>durationMs — 发送耗时（毫秒）
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public class NotifyAuditService {

  /** 专用审计 Logger，通过 logback 配置独立输出 */
  private static final Logger AUDIT_LOG = LoggerFactory.getLogger("NOTIFY_AUDIT");

  /** 接收者脱敏掩码长度 */
  private static final int MASK_KEEP_LENGTH = 3;

  private static final int MASK_MIN_LENGTH = 4;

  private static final int MAX_ERROR_LENGTH = 500;

  /**
   * 审计通知发送结果
   *
   * @param channel 通知渠道
   * @param receiver 接收者标识
   * @param title 消息标题
   * @param result 发送结果
   * @param durationMs 发送耗时（毫秒）
   * @param templateCode 模板编码（可为 null）
   */
  public void audit(
      NotifyChannel channel,
      String receiver,
      String title,
      NotifySendResult result,
      long durationMs,
      String templateCode) {
    Map<String, Object> auditEntry = new LinkedHashMap<>(16);
    auditEntry.put("timestamp", Instant.now().toString());
    auditEntry.put("traceId", NotifyTraceContext.getTraceId());
    auditEntry.put("channel", channel != null ? channel.getName() : "unknown");
    auditEntry.put("receiver_mask", maskReceiver(receiver));
    auditEntry.put("status", result.isSuccess() ? "success" : "failure");
    auditEntry.put("durationMs", durationMs);

    if (result.getMessageId() != null) {
      auditEntry.put("messageId", result.getMessageId());
    }
    if (!result.isSuccess() && result.getErrorMessage() != null) {
      auditEntry.put("error", truncateError(result.getErrorMessage()));
    }
    if (templateCode != null) {
      auditEntry.put("template", templateCode);
    }
    auditEntry.put("title_hash", hashTitle(title));

    AUDIT_LOG.info(YdszJson.toJson(auditEntry));
  }

  /**
   * 审计通知发送结果（简化版本）
   *
   * @param channel 通知渠道
   * @param receiver 接收者
   * @param result 发送结果
   * @param durationMs 耗时
   */
  public void audit(
      NotifyChannel channel, String receiver, NotifySendResult result, long durationMs) {
    audit(channel, receiver, null, result, durationMs, null);
  }

  /**
   * 脱敏接收者标识
   *
   * <p>邮箱：保留前 3 字符 + ***@domain
   *
   * <p>手机号（≥7 位）：保留前 3 字符 + **** + 后 4 字符
   *
   * <p>短字符串（≤4 字符）：直接返回全掩码 ***，避免暴露过多信息
   *
   * <p>其他：保留前 2 字符 + ***
   *
   * @param receiver 原始接收者
   * @return 脱敏后的接收者
   */
  private String maskReceiver(String receiver) {
    if (receiver == null || receiver.isEmpty()) {
      return "null";
    }
    if (receiver.contains("@")) {
      int atIndex = receiver.indexOf('@');
      if (atIndex <= MASK_KEEP_LENGTH) {
        return "***" + receiver.substring(atIndex);
      }
      return receiver.substring(0, MASK_KEEP_LENGTH) + "***" + receiver.substring(atIndex);
    }
    if (receiver.length() >= 7) {
      return receiver.substring(0, MASK_KEEP_LENGTH)
          + "****"
          + receiver.substring(receiver.length() - 4);
    }
    if (receiver.length() > MASK_MIN_LENGTH) {
      return receiver.substring(0, 2) + "***";
    }
    return "***";
  }

  /**
   * 计算标题哈希（用于关联同一通知的不同发送实例，不记录明文标题以保护隐私）
   *
   * @param title 消息标题
   * @return 标题哈希值
   */
  private String hashTitle(String title) {
    if (title == null || title.isEmpty()) {
      return "null";
    }
    return Integer.toHexString(title.hashCode());
  }

  /**
   * 截断错误信息，防止审计日志过长
   *
   * @param error 错误信息
   * @return 截断后的错误信息（最大 500 字符）
   */
  private String truncateError(String error) {
    if (error == null) {
      return "null";
    }
    return error.length() > MAX_ERROR_LENGTH ? error.substring(0, MAX_ERROR_LENGTH) + "..." : error;
  }
}
