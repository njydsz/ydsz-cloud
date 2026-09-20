package com.njydsz.common.netty.handler;

import java.util.concurrent.TimeUnit;

import io.netty.handler.timeout.IdleStateHandler;
import lombok.RequiredArgsConstructor;

/**
 * 空闲检测处理器工厂。
 *
 * <p>封装 {@link IdleStateHandler} 的创建逻辑，支持配置读/写/全双工空闲超时。 当 Channel 在指定时间内无读/写活动时，触发 {@link
 * io.netty.handler.timeout.IdleStateEvent}， 业务侧可通过 {@code @ChannelHandler.Sharable} 的 {@code
 * userEventTriggered} 处理。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @deprecated 可直接内联创建 {@code new IdleStateHandler(readerIdle, writerIdle, allIdle, TimeUnit.SECONDS)}，
 *     无需额外工厂类。{@link com.njydsz.common.netty.server.AbstractNettyServer} 和 {@link
 *      com.njydsz.common.netty.client.AbstractNettyClient} 已内联此逻辑。
 */
@Deprecated(since = "26.09.01", forRemoval = false)
@RequiredArgsConstructor
public class IdleStateHandlerFactory {

  private final long readerIdleSeconds;
  private final long writerIdleSeconds;
  private final long allIdleSeconds;

  /**
   * 创建 IdleStateHandler 实例。
   *
   * @return IdleStateHandler
   */
  public IdleStateHandler create() {
    return new IdleStateHandler(
        readerIdleSeconds, writerIdleSeconds, allIdleSeconds, TimeUnit.SECONDS);
  }
}
