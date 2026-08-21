package com.njydsz.message.server.channel.recall;

import java.time.Duration;
import java.time.LocalDateTime;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import com.njydsz.message.infra.entity.MsgLog;

/**
 * 企业微信应用消息（WECOM_APP）撤回实现。
 *
 * <p>通过企业微信开放平台 API 撤回已发送的应用消息。仅支持企业内部应用消息（WECOM_APP），
 * 不支持群机器人消息（WECOM）撤回。
 *
 * <p>时效窗口：消息发送后 2 分钟内可撤回。超过此窗口则仅做本地标记。
 *
 * <p>实际生产环境需接入企微 SDK 或调用 {@code /cgi-bin/message/recall} 接口，
 * 本实现为模拟框架，预留 API 调用位。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
public class WeComRecallChannel implements RecallChannel {

  /** 企微应用消息撤回时效窗口：2 分钟 */
  private static final Duration RECALL_WINDOW = Duration.ofMinutes(2);

  /** 通道类型标识 */
  private static final String CHANNEL_TYPE = "WECOM_APP";

  @Override
  public String channelType() {
    return CHANNEL_TYPE;
  }

  @Override
  public RecallResult recall(MsgLog log) {
    log.debug(
        "[RecallChannel] WECOM_APP 撤回尝试: msgId={} traceId={}",
        log.getMsgId(),
        log.getTraceId());

    // 时效窗口检查
    if (isBeyondRecallWindow(log)) {
      log.warn(
          "[RecallChannel] WECOM_APP 超出撤回时效窗口({}分钟),仅本地标记: msgId={}",
          RECALL_WINDOW.toMinutes(),
          log.getMsgId());
      return RecallResult.localOnly();
    }

    // 获取企微消息唯一标识（msgId），用于撤回 API 调用
    String wecomMsgId = resolveWeComMsgId(log);
    if (wecomMsgId == null || wecomMsgId.isBlank()) {
      log.warn(
          "[RecallChannel] WECOM_APP 无法获取企微消息 ID,仅本地标记: msgId={}",
          log.getMsgId());
      return RecallResult.localOnly();
    }

    // TODO: 接入企微 SDK 或调用 /cgi-bin/message/recall 接口实现真正的平台撤回
    // 当前为模拟实现，假设撤回成功
    log.info(
        "[RecallChannel] WECOM_APP 平台撤回成功(模拟): msgId={} wecomMsgId={}",
        log.getMsgId(),
        wecomMsgId);
    return RecallResult.platformSuccess();
  }

  /**
   * 检查消息是否超出撤回时效窗口。
   *
   * @param log 消息日志
   * @return true 表示已超出窗口
   */
  private boolean isBeyondRecallWindow(MsgLog log) {
    LocalDateTime createdAt = log.getCreatedAt();
    if (createdAt == null) {
      return true;
    }
    return Duration.between(createdAt, LocalDateTime.now()).compareTo(RECALL_WINDOW) > 0;
  }

  /**
   * 解析企微消息唯一标识。
   *
   * <p>优先使用 providerTraceId（服务商回执 ID），回退到 traceId。
   *
   * @param log 消息日志
   * @return 企微消息 ID，无法获取时返回 null
   */
  private String resolveWeComMsgId(MsgLog log) {
    if (log.getProviderTraceId() != null && !log.getProviderTraceId().isBlank()) {
      return log.getProviderTraceId();
    }
    if (log.getTraceId() != null && !log.getTraceId().isBlank()) {
      return log.getTraceId();
    }
    return null;
  }
}
