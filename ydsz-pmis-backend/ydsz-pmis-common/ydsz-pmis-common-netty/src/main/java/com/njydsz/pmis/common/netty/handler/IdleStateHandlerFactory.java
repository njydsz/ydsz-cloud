package com.njydsz.pmis.common.netty.handler;

import java.util.concurrent.TimeUnit;

import io.netty.handler.timeout.IdleStateHandler;
import lombok.RequiredArgsConstructor;

/**
 * 空闲检测处理器工厂。
 *
 * <p>封装 {@link IdleStateHandler} 的创建逻辑，支持配置读/写/全双工空闲超时。
 * 当 Channel 在指定时间内无读/写活动时，触发 {@link io.netty.handler.timeout.IdleStateEvent}，
 * 业务侧可通过 {@code @ChannelHandler.Sharable} 的 {@code userEventTriggered} 处理。
 *
 * @author ydsz-pmis-team
 * @since 1.3.0
 */
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
                readerIdleSeconds,
                writerIdleSeconds,
                allIdleSeconds,
                TimeUnit.SECONDS);
    }
}
