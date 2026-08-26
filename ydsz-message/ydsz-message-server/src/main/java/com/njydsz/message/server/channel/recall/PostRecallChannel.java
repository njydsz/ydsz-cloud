package com.njydsz.message.server.channel.recall;

import java.time.Duration;
import java.time.LocalDateTime;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import com.njydsz.message.domain.vo.MsgLogVO;

/**
 * POST 回调通道（POST）撤回实现。
 *
 * <p>通过 IM 开放平台 API 撤回已发送的群机器人消息。
 *
 * <p>时效窗口：消息发送后 1 分钟内可撤回。部分平台撤回窗口较短，超过后仅做本地标记。
 *
 * <p>实际生产环境需调用平台消息撤回接口，本实现为模拟框架，预留 API 调用位。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
public class PostRecallChannel implements RecallChannel {

  /** POST 通道撤回时效窗口：1 分钟 */
  private static final Duration RECALL_WINDOW = Duration.ofMinutes(1);

  /** 通道类型标识 */
  private static final String CHANNEL_TYPE = "POST";

  @Override
  public String channelType() {
    return CHANNEL_TYPE;
  }

  @Override
  public RecallResult recall(MsgLogVO log) {
    log.debug(
        "[RecallChannel] POST 撤回尝试: msgId={} traceId={}",
        log.getMsgId(),
        log.getTraceId());

    // 时效窗口检查
    if (isBeyondRecallWindow(log)) {
      log.warn(
          "[RecallChannel] POST 超出撤回时效窗口({}分钟),仅本地标记: msgId={}",
          RECALL_WINDOW.toMinutes(),
          log.getMsgId());
      return RecallResult.localOnly();
    }

    // 获取消息唯一标识（message_id），用于撤回 API 调用
    String postMsgId = resolvePostMsgId(log);
    if (postMsgId == null || postMsgId.isBlank()) {
      log.warn(
          "[RecallChannel] POST 无法获取消息 ID,仅本地标记: msgId={}",
          log.getMsgId());
      return RecallResult.localOnly();
    }

    // TODO: 调用平台消息撤回接口实现真正的平台撤回
    // 当前为模拟实现，假设撤回成功
    log.info(
        "[RecallChannel] POST 平台撤回成功(模拟): msgId={} postMsgId={}",
        log.getMsgId(),
        postMsgId);
    return RecallResult.platformSuccess();
  }

  /**
   * 检查消息是否超出撤回时效窗口。
   *
   * @param log 消息日志
   * @return true 表示已超出窗口
   */
  private boolean isBeyondRecallWindow(MsgLogVO log) {
    LocalDateTime createdAt = log.getCreatedAt();
    if (createdAt == null) {
      return true;
    }
    return Duration.between(createdAt, LocalDateTime.now()).compareTo(RECALL_WINDOW) > 0;
  }

  /**
   * 解析消息唯一标识。
   *
   * <p>优先使用 providerTraceId（服务商回执 ID），回退到 traceId。
   *
   * @param log 消息日志
   * @return 消息 ID，无法获取时返回 null
   */
  private String resolvePostMsgId(MsgLogVO log) {
    if (log.getProviderTraceId() != null && !log.getProviderTraceId().isBlank()) {
      return log.getProviderTraceId();
    }
    if (log.getTraceId() != null && !log.getTraceId().isBlank()) {
      return log.getTraceId();
    }
    return null;
  }
}
