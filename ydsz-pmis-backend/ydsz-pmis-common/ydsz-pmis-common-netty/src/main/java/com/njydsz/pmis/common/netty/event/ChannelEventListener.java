package com.njydsz.pmis.common.netty.event;

import io.netty.channel.Channel;

/**
 * Channel 事件监听器接口。
 *
 * <p>业务方实现此接口，通过 Spring Bean 自动注册到 {@link ChannelEventDispatcher}，
 * 在 Channel 连接/断开时收到回调通知。
 *
 * <p>使用方式：
 * <pre>{@code
 * @Component
 * public class MyChannelListener implements ChannelEventListener {
 *     @Override
 *     public void onChannelActive(Channel channel) {
 *         log.info("新连接: {}", channel.remoteAddress());
 *     }
 * }
 * }</pre>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface ChannelEventListener {

    /**
     * Channel 激活（新连接建立）时回调。
     *
     * @param channel 新建的 Channel
     */
    default void onChannelActive(Channel channel) {
    }

    /**
     * Channel 断开（连接关闭）时回调。
     *
     * @param channel 关闭的 Channel
     */
    default void onChannelInactive(Channel channel) {
    }
}
