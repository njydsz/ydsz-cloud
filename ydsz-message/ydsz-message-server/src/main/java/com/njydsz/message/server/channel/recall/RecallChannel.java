package com.njydsz.message.server.channel.recall;

import com.njydsz.message.infra.entity.MsgLog;

/**
 * 通道消息撤回策略接口（P2-F2: 跨通道消息撤回能力扩展）。
 *
 * <p>不同通道对"撤回"的支持程度差异较大：
 *
 * <ul>
 *   <li><b>站内信 / IM 类</b>（INAPP / DINGTALK / WECOM / FEISHU）：支持调用平台 API 撤回（有限时间窗口）
 *   <li><b>SMS / EMAIL</b>：仅能标记状态为 RECALLED（无法真正撤回已发送物理介质）
 *   <li><b>WEBHOOK / PUSH</b>：仅标记
 * </ul>
 *
 * <p>实现类通过 {@link #channelType()} 声明支持的通道类型，由 {@link RecallChannelRouter} 按需路由。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface RecallChannel {

  /**
   * 支持的通道类型（与 {@code MessageChannel.channelType()} 返回值一致，如 INAPP / DINGTALK / SMS）。
   *
   * @return 通道类型标识
   */
  String channelType();

  /**
   * 尝试撤回指定消息。
   *
   * <p>实现类根据通道能力：能真正调用平台 API 的尽量调用（如钉钉/飞书机器人）； 不能的则仅做状态标记，返回的 {@code platformRecallSucceeded=false}
   * 表示仅本地标记。
   *
   * @param log 消息日志（含 channel / msgId / traceId 等关键信息）
   * @return 撤回结果（平台是否真正撤回 + 失败原因）
   */
  RecallResult recall(MsgLog log);

  /**
   * 撤回结果记录。
   *
   * @param platformRecallSucceeded true 表示平台 API 撤回成功；false 表示仅本地标记
   * @param failureReason 失败原因（成功时为 null）
   */
  record RecallResult(boolean platformRecallSucceeded, String failureReason) {

    /** 平台撤回成功。 */
    public static RecallResult platformSuccess() {
      return new RecallResult(true, null);
    }

    /** 仅本地标记（通道不支持 API 撤回）。 */
    public static RecallResult localOnly() {
      return new RecallResult(false, null);
    }

    /** 撤回失败。 */
    public static RecallResult failed(String reason) {
      return new RecallResult(false, reason);
    }
  }
}
