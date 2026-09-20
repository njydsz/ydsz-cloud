package com.njydsz.common.netty.codec;

import java.util.List;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.handler.codec.MessageToMessageDecoder;
import lombok.extern.slf4j.Slf4j;

/**
 * JSON 消息编解码器 — 基于 {@code YdszJson} 引擎实现消息序列化/反序列化。
 *
 * <p>此类已拆分为 {@link JsonMessageEncoder}、{@link JsonMessageDecoder} 和 {@link JsonCodecUtil}， 保留此类仅为
 * 向后兼容。新代码请直接使用拆分后的类。
 *
 * <p>使用方式：
 *
 * <pre>{@code
 * // 在 Pipeline 中添加（推荐拆分后的编码器 + 解码器）
 * ch.pipeline().addLast(new JsonMessageEncoder&lt;&gt;(MyMessage.class));
 * ch.pipeline().addLast(new JsonMessageDecoder&lt;&gt;(MyMessage.class));
 * }</pre>
 *
 * <p>需配合 {@link LengthFieldFrameDecoder} 或 {@link LengthFieldCodec} 使用。
 *
 * @param <T> 业务消息类型
 * @author ydsz-team
 * @since 26.09.01
 * @deprecated 使用 {@link JsonMessageEncoder} + {@link JsonMessageDecoder} 替代，拆分后职责更清晰
 */
@Slf4j
@ChannelHandler.Sharable
@Deprecated(since = "26.09.01", forRemoval = false)
public class JsonMessageCodec<T> extends MessageToByteEncoder<T> {

  private final Class<T> messageClass;
  private final JsonMessageEncoder<T> delegateEncoder;

  /**
   * 构造 JSON 消息编解码器。
   *
   * @param messageClass 消息类型
   */
  public JsonMessageCodec(Class<T> messageClass) {
    this.messageClass = messageClass;
    this.delegateEncoder = new JsonMessageEncoder<>(messageClass);
  }

  /** 编码：将业务对象序列化为 JSON 字节流。 */
  @Override
  protected void encode(ChannelHandlerContext ctx, T msg, ByteBuf out) throws Exception {
    delegateEncoder.encode(ctx, msg, out);
  }

  /**
   * 解码：将 ByteBuf 反序列化为业务对象。
   *
   * <p>此方法为工具方法，供业务 Handler 在 channelRead 中调用。建议使用 {@link JsonCodecUtil#decode(ByteBuf, Class)}。
   *
   * @param buf ByteBuf
   * @return 业务对象
   */
  public T decode(ByteBuf buf) {
    return JsonCodecUtil.decode(buf, messageClass);
  }

  /**
   * 解码：将字节数组反序列化为业务对象。
   *
   * @param bytes 字节数组
   * @return 业务对象
   */
  public T decode(byte[] bytes) {
    return JsonCodecUtil.decode(bytes, messageClass);
  }

  /**
   * 创建入站解码 Handler（将 ByteBuf 自动解码为业务对象）。
   *
   * <p>等效于 {@code new JsonMessageDecoder<>(messageClass)}。
   *
   * @return MessageToMessageDecoder
   */
  public MessageToMessageDecoder<ByteBuf> createDecoder() {
    return new JsonMessageDecoder<>(messageClass);
  }
}
