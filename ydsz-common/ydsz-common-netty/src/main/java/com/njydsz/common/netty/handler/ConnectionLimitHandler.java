package com.njydsz.common.netty.handler;

import java.util.concurrent.atomic.AtomicInteger;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import lombok.extern.slf4j.Slf4j;

/**
 * 连接数限制 Handler。
 *
 * <p>在 Pipeline 前端限制 Server 的最大并发连接数，当连接数超过阈值时主动关闭新连接， 防止恶意或异常客户端耗尽文件描述符和内存。
 *
 * <p>使用 {@link ChannelHandler.Sharable} 注解允许在多个 Channel 间共享同一实例。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@ChannelHandler.Sharable
public class ConnectionLimitHandler extends ChannelInboundHandlerAdapter {

  private static final long serialVersionUID = 1L;

  private final int maxConnections;
  private final AtomicInteger activeConnections = new AtomicInteger(0);

  /**
   * 构造连接限制 Handler。
   *
   * @param maxConnections 最大连接数
   */
  public ConnectionLimitHandler(int maxConnections) {
    this.maxConnections = maxConnections;
  }

  @Override
  public void channelActive(ChannelHandlerContext ctx) throws Exception {
    int current = activeConnections.incrementAndGet();
    if (current > maxConnections) {
      activeConnections.decrementAndGet();
      log.warn(
          "[Netty-Server] 连接数超过上限 ({} > {})，关闭新连接: {}",
          current,
          maxConnections,
          ctx.channel().remoteAddress());
      ctx.close();
      return;
    }
    super.channelActive(ctx);
  }

  @Override
  public void channelInactive(ChannelHandlerContext ctx) throws Exception {
    activeConnections.decrementAndGet();
    super.channelInactive(ctx);
  }

  /**
   * 获取当前活跃连接数。
   *
   * @return 活跃连接数
   */
  public int getActiveConnections() {
    return activeConnections.get();
  }

  /**
   * 获取最大连接数限制。
   *
   * @return 最大连接数
   */
  public int getMaxConnections() {
    return maxConnections;
  }
}
