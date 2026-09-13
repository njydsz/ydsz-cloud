package com.njydsz.common.netty.util;

import io.netty.channel.ChannelOption;

/**
 * Netty 通道选项常量 — 封装常用的 {@link ChannelOption} 常量引用，为业务模块提供类型安全的配置入口。
 *
 * <p>业务模块应通过本类的静态字段引用通道选项，避免直接 {@code import io.netty.channel.ChannelOption}。
 *
 * <h3>典型用法（Spring Reactor Netty）</h3>
 *
 * <pre>
 * HttpClient httpClient = HttpClient.create(provider)
 *     .option(NettyChannelOptions.CONNECT_TIMEOUT_MILLIS, 5000)
 *     .responseTimeout(Duration.ofSeconds(60));
 * </pre>
 *
 * <h3>当前封装的选项</h3>
 *
 * <ul>
 *   <li>{@link #CONNECT_TIMEOUT_MILLIS} — TCP 连接建立超时</li>
 *   <li>{@link #SO_KEEPALIVE} — TCP Keepalive 探测</li>
 *   <li>{@link #TCP_NODELAY} — 禁用 Nagle algorithm（低延迟）</li>
 *   <li>{@link #SO_SNDBUF} — 发送缓冲区大小</li>
 *   <li>{@link #SO_RCVBUF} — 接收缓冲区大小</li>
 * </ul>
 *
 * <p>如需更多选项，直接在本类追加对应的静态字段（参考 {@link ChannelOption} Javadoc 的选项全列表）。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see ChannelOption
 */
public final class NettyChannelOptions {

  /** 私有构造 — 工具类禁止实例化 */
  private NettyChannelOptions() {}

  /**
   * TCP 连接建立超时（毫秒）。
   *
   * <p>等价于 {@code ChannelOption.CONNECT_TIMEOUT_MILLIS}。
   */
  public static final ChannelOption<Integer> CONNECT_TIMEOUT_MILLIS =
      ChannelOption.CONNECT_TIMEOUT_MILLIS;

  /**
   * TCP Keepalive 探测。
   *
   * <p>等价于 {@code ChannelOption.SO_KEEPALIVE}。
   */
  public static final ChannelOption<Boolean> SO_KEEPALIVE = ChannelOption.SO_KEEPALIVE;

  /**
   * 禁用 Nagle 算法，降低小包延迟。
   *
   * <p>等价于 {@code ChannelOption.TCP_NODELAY}。
   */
  public static final ChannelOption<Boolean> TCP_NODELAY = ChannelOption.TCP_NODELAY;

  /**
   * 发送缓冲区大小（字节）。
   *
   * <p>等价于 {@code ChannelOption.SO_SNDBUF}。
   */
  public static final ChannelOption<Integer> SO_SNDBUF = ChannelOption.SO_SNDBUF;

  /**
   * 接收缓冲区大小（字节）。
   *
   * <p>等价于 {@code ChannelOption.SO_RCVBUF}。
   */
  public static final ChannelOption<Integer> SO_RCVBUF = ChannelOption.SO_RCVBUF;
}
