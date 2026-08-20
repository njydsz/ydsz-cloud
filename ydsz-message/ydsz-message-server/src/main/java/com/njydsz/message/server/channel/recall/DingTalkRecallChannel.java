package com.njydsz.message.server.channel.recall;

import java.time.Duration;
import java.time.LocalDateTime;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import com.njydsz.message.domain.model.core.MsgLog;
import com.njydsz.message.domain.enums.core.MessageChannelEnum;

/**
 * 钉钉（DINGTALK）撤回实现。
 *
 * <p>通过钉钉开放平台 API 撤回已发送的群机器人消息。
 *
 * <p><b>通道限制：</b>
 *
 * <ul>
 *   <li>DINGTALK（群机器人）：支持撤回，时效窗口 2 分钟
 *   <li>DINGTALK_WORK（工作通知）：不支持平台 API 撤回，仅本地标记
 * </ul>
 *
 * <p>实际生产环境需调用钉钉机器人消息撤回接口（若有）或标记处理，
 * 本实现为模拟框架，预留 API 调用位。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
public class DingTalkRecallChannel implements RecallChannel {

  /** 钉钉群机器人撤回时效窗口：2 分钟 */
  private static final Duration RECALL_WINDOW = Duration.ofMinutes(2);

  /** 通道类型标识：仅 DINGTALK（群机器人）支持撤回 */
  private static final String CHANNEL_TYPE = "DINGTALK";

  @Override
  public String channelType() {
    return CHANNEL_TYPE;
  }

  @Override
  public RecallResult recall(MsgLog log) {
    log.debug(
        "[RecallChannel] DINGTALK 撤回尝试: msgId={} traceId={}",
        log.getMsgId(),
        log.getTraceId());

    // 通道能力校验：DINGTALK_WORK（工作通知）不支持平台 API 撤回
    if (log.getChannel() == MessageChannelEnum.DINGTALK_WORK) {
      log.info(
          "[RecallChannel] DINGTALK_WORK 不支持平台撤回,仅本地标记: msgId={}",
          log.getMsgId());
      return RecallResult.localOnly();
    }

    // 时效窗口检查
    if (isBeyondRecallWindow(log)) {
      log.warn(
          "[RecallChannel] DINGTALK 超出撤回时效窗口({}分钟),仅本地标记: msgId={}",
          RECALL_WINDOW.toMinutes(),
          log.getMsgId());
      return RecallResult.localOnly();
    }

    // 获取钉钉消息唯一标识，用于撤回 API 调用
    String dingtalkMsgId = resolveDingTalkMsgId(log);
    if (dingtalkMsgId == null || dingtalkMsgId.isBlank()) {
      log.warn(
          "[RecallChannel] DINGTALK 无法获取消息 ID,仅本地标记: msgId={}",
          log.getMsgId());
      return RecallResult.localOnly();
    }

    // TODO: 调用钉钉机器人消息撤回接口实现真正的平台撤回
    // 当前为模拟实现，假设撤回成功
    log.info(
        "[RecallChannel] DINGTALK 平台撤回成功(模拟): msgId={} dingtalkMsgId={}",
        log.getMsgId(),
        dingtalkMsgId);
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
   * 解析钉钉消息唯一标识。
   *
   * <p>优先使用 providerTraceId（服务商回执 ID），回退到 traceId。
   *
   * @param log 消息日志
   * @return 钉钉消息 ID，无法获取时返回 null
   */
  private String resolveDingTalkMsgId(MsgLog log) {
    if (log.getProviderTraceId() != null && !log.getProviderTraceId().isBlank()) {
      return log.getProviderTraceId();
    }
    if (log.getTraceId() != null && !log.getTraceId().isBlank()) {
      return log.getTraceId();
    }
    return null;
  }
}
