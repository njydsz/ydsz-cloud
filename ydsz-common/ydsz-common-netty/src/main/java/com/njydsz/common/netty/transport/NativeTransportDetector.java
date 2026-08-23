package com.njydsz.common.netty.transport;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.epoll.EpollSocketChannel;
import io.netty.channel.kqueue.KQueue;
import io.netty.channel.kqueue.KQueueEventLoopGroup;
import io.netty.channel.kqueue.KQueueServerSocketChannel;
import io.netty.channel.kqueue.KQueueSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.ServerSocketChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.concurrent.DefaultThreadFactory;
import lombok.extern.slf4j.Slf4j;

import com.njydsz.common.netty.exception.NettyException;

/**
 * 原生传输检测器 — 自动检测并选择最优的 Netty 传输方式。
 *
 * <p>优先级：Epoll (Linux) > KQueue (macOS/BSD) > NIO (通用)
 *
 * <p>Epoll 在 Linux 上提供边缘触发、零拷贝等性能优势； KQueue 在 macOS/BSD 上提供类似优势； NIO 是跨平台通用方案。
 *
 * <p>检测逻辑通过 {@link Epoll#isAvailable()} / {@link KQueue#isAvailable()} 安全检测原生库是否加载，不会抛出 {@link
 * UnsatisfiedLinkError}。
 *
 * <p>配置通过 {@code ydsz.netty.native-transport} 控制：
 *
 * <ul>
 *   <li>{@code auto}（默认）— 自动检测最佳传输
 *   <li>{@code enabled} — 强制使用原生传输（不可用时抛异常）
 *   <li>{@code disabled} — 禁用原生传输，强制使用 NIO
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public final class NativeTransportDetector {

  /** 原生传输类型 */
  public enum TransportType {
    /** Linux epoll */
    EPOLL,
    /** macOS kqueue */
    KQUEUE,
    /** 纯 Java NIO */
    NIO
  }

  private NativeTransportDetector() {}

  /**
   * 检测当前环境可用的传输类型。
   *
   * @param mode 模式（auto / enabled / disabled）
   * @return 传输类型
   */
  public static TransportType detect(String mode) {
    if ("disabled".equalsIgnoreCase(mode)) {
      log.info("[Netty-Transport] 原生传输已禁用，使用 NIO");
      return TransportType.NIO;
    }

    if (Epoll.isAvailable()) {
      log.info("[Netty-Transport] 检测到 Epoll 原生传输可用（Linux）");
      return TransportType.EPOLL;
    }

    if (KQueue.isAvailable()) {
      log.info("[Netty-Transport] 检测到 KQueue 原生传输可用（macOS/BSD）");
      return TransportType.KQUEUE;
    }

    if ("enabled".equalsIgnoreCase(mode)) {
      throw new NettyException("原生传输已强制启用，但当前环境不支持 Epoll 或 KQueue");
    }

    log.info("[Netty-Transport] 原生传输不可用，使用 NIO");
    return TransportType.NIO;
  }

  /**
   * 获取 Server Socket Channel 类。
   *
   * @param type 传输类型
   * @return Channel 类
   */
  public static Class<? extends ServerSocketChannel> getServerSocketChannelClass(
      TransportType type) {
    return switch (type) {
      case EPOLL -> EpollServerSocketChannel.class;
      case KQUEUE -> KQueueServerSocketChannel.class;
      case NIO -> NioServerSocketChannel.class;
    };
  }

  /**
   * 获取 Client Socket Channel 类。
   *
   * @param type 传输类型
   * @return Channel 类
   */
  public static Class<? extends SocketChannel> getSocketChannelClass(TransportType type) {
    return switch (type) {
      case EPOLL -> EpollSocketChannel.class;
      case KQUEUE -> KQueueSocketChannel.class;
      case NIO -> NioSocketChannel.class;
    };
  }

  /**
   * 创建指定传输类型的 EventLoopGroup。
   *
   * @param type 传输类型
   * @param threads 线程数
   * @param threadNamePrefix 线程名前缀
   * @return EventLoopGroup
   */
  public static EventLoopGroup createEventLoopGroup(
      TransportType type, int threads, String threadNamePrefix) {
    return switch (type) {
      case EPOLL -> new EpollEventLoopGroup(threads, new DefaultThreadFactory(threadNamePrefix));
      case KQUEUE -> new KQueueEventLoopGroup(threads, new DefaultThreadFactory(threadNamePrefix));
      case NIO -> new NioEventLoopGroup(threads, new DefaultThreadFactory(threadNamePrefix));
    };
  }
}
