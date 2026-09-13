package com.njydsz.common.netty.util;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.util.CharsetUtil;

/**
 * Netty 缓冲区工具类 — 封装 ByteBuf ↔ String 的常用转换，统一字符集与异常处理。
 *
 * <p>业务模块应通过本工具类操作 Netty 缓冲区，避免直接 import {@code io.netty.buffer.*} /
 * {@code io.netty.util.CharsetUtil}。
 *
 * <h3>典型用法</h3>
 *
 * <pre>
 * // String → ByteBuf（用于 writeAndFlush）
 * ByteBuf buf = NettyBufferUtils.toUtf8ByteBuf(json);
 *
 * // ByteBuf → String（用于 channelRead 解析消息体）
 * String text = NettyBufferUtils.toUtf8String(buf);
 * </pre>
 *
 * <h3>线程安全</h3>
 *
 * <p>本工具类方法均为纯函数（无共享可变状态），可在多线程环境中安全调用。
 *
 * <p>返回的 {@link ByteBuf} 为 Unpooled 堆缓冲区；使用后由 Netty 管线自动释放（{@code channelRead}
 * 中的 {@code ByteBuf} 到达管线末端会被 {@link io.netty.channel.ChannelPipeline} 释放）。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public final class NettyBufferUtils {

  /** 私有构造 — 工具类禁止实例化 */
  private NettyBufferUtils() {}

  /**
   * 将 UTF-8 文本字符串编码为 Netty ByteBuf（堆内存）。
   *
   * <p>等价于 {@code Unpooled.copiedBuffer(text, CharsetUtil.UTF_8)}。
   *
   * @param text 待编码的文本字符串（允许 {@code null}，返回空 ByteBuf）
   * @return UTF-8 编码的 ByteBuf
   */
  public static ByteBuf toUtf8ByteBuf(String text) {
    if (text == null || text.isEmpty()) {
      return Unpooled.EMPTY_BUFFER;
    }
    return Unpooled.copiedBuffer(text, CharsetUtil.UTF_8);
  }

  /**
   * 将 Netty ByteBuf 解码为 UTF-8 文本字符串。
   *
   * <p>等价于 {@code buf.toString(CharsetUtil.UTF_8)}，但增加了空值保护。
   *
   * @param buf 待解码的 ByteBuf（允许 {@code null}，返回 {@code null}）
   * @return UTF-8 解码后的字符串；{@code buf} 为 {@code null} 时返回 {@code null}
   */
  public static String toUtf8String(ByteBuf buf) {
    if (buf == null) {
      return null;
    }
    return buf.toString(CharsetUtil.UTF_8);
  }
}
