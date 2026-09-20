package com.njydsz.common.netty.codec;

import java.nio.charset.StandardCharsets;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import lombok.extern.slf4j.Slf4j;

import com.njydsz.common.json.YdszJson;

/**
 * JSON 消息编码器 — 将业务对象序列化为 JSON 字节流写入 ByteBuf。
 *
 * <p>是 {@link JsonMessageCodec} 拆分后的独立编码侧，遵循 Netty 编解码器单一职责原则。
 *
 * <p>使用方式：
 *
 * <pre>{@code
 * // 在 Pipeline 中添加编码器
 * ch.pipeline().addLast(new JsonMessageEncoder&lt;&gt;(MyMessage.class));
 * }</pre>
 *
 * <p>需配合 {@link LengthFieldCodec} 的 LengthFieldPrepender 使用，确保帧边界完整。
 *
 * <p>此类是 {@code @ChannelHandler.Sharable} 的，可在多个 Channel 间安全共享。
 *
 * @param <T> 业务消息类型
 * @author ydsz-team
 * @since 26.09.01
 * @see JsonMessageDecoder
 * @see JsonCodecUtil
 */
@Slf4j
@ChannelHandler.Sharable
public class JsonMessageEncoder<T> extends MessageToByteEncoder<T> {

  private final Class<T> messageClass;

  /**
   * 构造 JSON 消息编码器。
   *
   * @param messageClass 消息类型（用于日志诊断，不参与编码逻辑）
   */
  public JsonMessageEncoder(Class<T> messageClass) {
    this.messageClass = messageClass;
  }

  /** 编码：将业务对象序列化为 JSON 字节流写入 ByteBuf。 */
  @Override
  protected void encode(ChannelHandlerContext ctx, T msg, ByteBuf out) throws Exception {
    byte[] bytes = JsonCodecUtil.encodeToBytes(msg);
    out.writeBytes(bytes);
  }

  /**
   * 获取关联的消息类型。
   *
   * @return 消息 Class
   */
  public Class<T> getMessageClass() {
    return messageClass;
  }

  /** 返回静态类型的 toString，显示 Encoder + 消息类型名。 */
  @Override
  public String toString() {
    return "JsonMessageEncoder(" + messageClass.getSimpleName() + ")";
  }
}
