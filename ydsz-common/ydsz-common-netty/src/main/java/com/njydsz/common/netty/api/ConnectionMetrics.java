package com.njydsz.common.netty.api;

/**
 * 连接指标接口 — 定义网络连接模块的公共指标契约。
 *
 * <p>供 ydsz-common-netty、ydsz-common-socket 等网络模块实现， 统一连接数、消息数、字节数等核心指标的操作规范。
 *
 * <p>实现类应确保线程安全，所有计数操作支持高并发场景。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public interface ConnectionMetrics {

  /** 递增活跃通道/连接数。 */
  void incrementActiveChannels();

  /** 递减活跃通道/连接数（不会变为负数）。 */
  void decrementActiveChannels();

  /** 递增连接计数。 */
  void incrementConnections();

  /** 递增断开计数。 */
  void incrementDisconnections();

  /** 递增消息接收计数。 */
  void incrementMessagesReceived();

  /** 递增消息发送计数。 */
  void incrementMessagesSent();

  /**
   * 获取当前活跃通道/连接数。
   *
   * @return 活跃通道/连接数
   */
  long getActiveChannels();

  /**
   * 获取累计读取字节数。
   *
   * @return 读取字节数
   */
  long getTotalBytesRead();

  /**
   * 获取累计写入字节数。
   *
   * @return 写入字节数
   */
  long getTotalBytesWritten();
}
