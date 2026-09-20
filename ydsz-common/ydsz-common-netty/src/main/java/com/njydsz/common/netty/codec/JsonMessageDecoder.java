package com.njydsz.common.netty.codec;

import java.util.List;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import lombok.extern.slf4j.Slf4j;

/**
 * JSON 消息解码器 — 将 ByteBuf 自动反序列化为业务对象。
 *
 * <p>是 {@link JsonMessageCodec} 拆分后的独立解码侧，遵循 Netty 编解码器单一职责原则。
 *
 * <p>使用方式：
 *
 * <pre>{@code
 * // 在 Pipeline 中添加解码器
 * ch.pipeline().addLast(new JsonMessageDecoder&lt;&gt;(MyMessage.class));
 * }</pre>
 *
 * <p>需配合 {@link LengthFieldFrameDecoder} 或 {@link LengthFieldCodec} 使用， 确保 channelRead 收到的是完整帧的 ByteBuf。
 *
 * <p>此类是 {@code @ChannelHandler.Sharable} 的，可在多个 Channel 间安全共享。
 *
 * @param <T> 业务消息类型
 * @author ydsz-team
 * @since 26.09.01
 * @see JsonMessageEncoder
 * @see JsonCodecUtil
 */
@Slf4j
@ChannelHandler.Sharable
public class JsonMessageDecoder<T> extends MessageToMessageDecoder<ByteBuf> {

  private final Class<T> messageClass;

  /**
   * 构造 JSON 消息解码器。
   *
   * @param messageClass 消息类型
   */
  public JsonMessageDecoder(Class<T> messageClass) {
    this.messageClass = messageClass;
  }

  /** 解码：将 ByteBuf 反序列化为业务对象并加入输出列表。 */
  @Override
  protected void decode(ChannelHandlerContext ctx, ByteBuf msg, List<Object> out) throws Exception {
    T message = JsonCodecUtil.decode(msg, messageClass);
    if (message != null) {
      out.add(message);
    }
  }

  /**
   * 获取关联的消息类型。
   *
   * @return 消息 Class
   */
  public Class<T> getMessageClass() {
    return messageClass;
  }

  /** 返回静态类型的 toString，显示 Decoder + 消息类型名。 */
  @Override
  public String toString() {
    return "JsonMessageDecoder(" + messageClass.getSimpleName() + ")";
  }
}
