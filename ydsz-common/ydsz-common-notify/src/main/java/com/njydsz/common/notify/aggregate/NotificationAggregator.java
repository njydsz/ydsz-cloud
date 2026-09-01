package com.njydsz.common.notify.aggregate;

import java.util.List;
import java.util.Map;

import com.njydsz.common.notify.enums.NotifyChannel;
import com.njydsz.common.notify.enums.NotifyPriority;

/**
 * 通知聚合策略接口（P2-3）
 *
 * <p>将短时间内发送给同一用户的同类通知聚合为一条摘要，避免消息轰炸。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public interface NotificationAggregator {

  /**
   * 判断指定通知是否应该被聚合
   *
   * @param receiver 接收者
   * @param channel 通知渠道
   * @param templateCode 模板编码（可为 null）
   * @return true 表示应该聚合
   */
  boolean shouldAggregate(String receiver, NotifyChannel channel, String templateCode);

  /**
   * 将待聚合的消息列表合并为摘要内容
   *
   * @param messages 待聚合的消息列表
   * @return 聚合后的摘要内容和标题
   */
  AggregatedMessage aggregate(List<PendingMessage> messages);

  /**
   * 将消息加入聚合缓冲区
   *
   * @param receiver 接收者
   * @param channel 通知渠道
   * @param templateCode 模板编码
   * @param title 标题
   * @param content 内容
   * @param priority 优先级
   * @return true 表示消息已加入缓冲区（等待聚合），false 表示应立即发送
   */
  default boolean offer(
      String receiver,
      NotifyChannel channel,
      String templateCode,
      String title,
      String content,
      NotifyPriority priority) {
    return false;
  }

  /**
   * 刷新所有待聚合消息
   *
   * @return key 到聚合消息的映射
   */
  default Map<String, AggregatedMessage> flushAll() {
    return Map.of();
  }

  /**
   * 获取聚合时间窗口（秒）
   *
   * @return 聚合窗口秒数
   */
  int getAggregateWindowSeconds();

  /** 待聚合的消息 */
  class PendingMessage {

    private final String receiver;
    private final String title;
    private final String content;
    private final long timestamp;

    /**
     * 构造待聚合消息
     *
     * @param receiver 接收者
     * @param title 标题
     * @param content 内容
     * @param timestamp 时间戳
     */
    public PendingMessage(String receiver, String title, String content, long timestamp) {
      this.receiver = receiver;
      this.title = title;
      this.content = content;
      this.timestamp = timestamp;
    }

    public String getReceiver() {
      return receiver;
    }

    public String getTitle() {
      return title;
    }

    public String getContent() {
      return content;
    }

    public long getTimestamp() {
      return timestamp;
    }
  }

  /** 聚合后的消息 */
  class AggregatedMessage {

    private final String title;
    private final String content;
    private final int originalCount;
    private final NotifyChannel channel;

    /**
     * 构造聚合消息
     *
     * @param title 聚合标题
     * @param content 聚合内容
     * @param originalCount 原始消息数量
     */
    public AggregatedMessage(String title, String content, int originalCount) {
      this(title, content, originalCount, null);
    }

    /**
     * 构造聚合消息（含渠道信息）
     *
     * @param title 聚合标题
     * @param content 聚合内容
     * @param originalCount 原始消息数量
     * @param channel 通知渠道
     */
    public AggregatedMessage(
        String title, String content, int originalCount, NotifyChannel channel) {
      this.title = title;
      this.content = content;
      this.originalCount = originalCount;
      this.channel = channel;
    }

    public String getTitle() {
      return title;
    }

    public String getContent() {
      return content;
    }

    public int getOriginalCount() {
      return originalCount;
    }

    public NotifyChannel getChannel() {
      return channel;
    }
  }
}
