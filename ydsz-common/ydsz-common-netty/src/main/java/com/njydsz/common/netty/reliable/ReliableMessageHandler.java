package com.njydsz.common.netty.reliable;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import lombok.extern.slf4j.Slf4j;

/**
 * 可靠投递消息 Handler — 自动处理 {@link ReliableMessage} 的 ACK 逻辑。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>收到 ReliableMessage 时：若 requiresAck=true，自动回复 {@link AckMessage}
 *   <li>业务消息（非 ReliableMessage）：透传给下一个 Handler
 * </ul>
 *
 * <p><b>注意：此 Handler 是 {@code @Sharable} 的</b>，因为它的 ACK 逻辑是纯函数式的， 不依赖 Channel 的元状态。
 *
 * <p>典型用法：
 *
 * <pre>{@code
 * &#64;Override
 * protected void initChannelPipeline(SocketChannel ch) {
 *     ch.pipeline().addLast(LengthFieldCodec.createDecoder());
 *     ch.pipeline().addLast("reliableHandler", new ReliableMessageHandler());
 *     ch.pipeline().addLast(new MyBusinessHandler());
 * }
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see ReliableMessage
 * @see AckMessage
 */
@Slf4j
@ChannelHandler.Sharable
public class ReliableMessageHandler extends ChannelInboundHandlerAdapter {

  @Override
  public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
    if (msg instanceof ReliableMessage reliable) {
      if (reliable.isRequiresAck()) {
        // 自动回复 ACK
        ctx.writeAndFlush(AckMessage.ack(reliable.getMsgId()));
        log.debug("[Netty-Reliable] 收到可靠消息, msgId={}, 已自动回复 ACK", reliable.getMsgId());
      }
      // 透传业务对象到下一个 Handler
      ctx.fireChannelRead(reliable.getBody());
    } else {
      // 非可靠消息直接透传
      super.channelRead(ctx, msg);
    }
  }
}
