package com.njydsz.common.socket.metric;

/**
 * 网络连接指标顶层契约（P2-9）。
 *
 * <p>定义网络连接模块（WebSocket / Netty / 长连接等）的公共指标操作规范， 各实现类应确保线程安全，所有计数操作支持高并发场景。
 *
 * <p>涵盖的核心指标维度：
 *
 * <ul>
 *   <li>连接维度：活跃连接数、累计连接数、累计断开数
 *   <li>消息维度：消息接收数、消息发送数
 *   <li>流量维度：累计读取字节数、累计写入字节数
 * </ul>
 *
 * <p>实现类通过 Micrometer 注册到 {@code MeterRegistry}， 当 {@code MeterRegistry} 不在 classpath
 * 时降级为空操作（no-op）。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public interface NetworkMetrics {

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
