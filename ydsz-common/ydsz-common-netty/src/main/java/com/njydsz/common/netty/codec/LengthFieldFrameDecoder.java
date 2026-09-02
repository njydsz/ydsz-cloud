package com.njydsz.common.netty.codec;

import io.netty.handler.codec.LengthFieldBasedFrameDecoder;

/**
 * 通用长度域帧解码器（解决 TCP 粘包/半包问题）。
 *
 * <p>基于 Netty {@link LengthFieldBasedFrameDecoder}，默认协议格式：
 *
 * <pre>
 * +--------+--------+------------------+
 * | Length  | Type   | Payload          |
 * | 4 bytes | 1 byte | Length bytes     |
 * +--------+--------+------------------+
 * </pre>
 *
 * <p>Length 字段为大端 4 字节整数，表示 Payload 长度（不含 Length 和 Type 字段）。 maxFrameLength 默认 1MB，可通过构造参数调整。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public class LengthFieldFrameDecoder extends LengthFieldBasedFrameDecoder {

  /** 默认最大帧长度（1MB） */
  public static final int DEFAULT_MAX_FRAME_LENGTH = 1024 * 1024;

  /** 长度字段偏移量 */
  private static final int LENGTH_FIELD_OFFSET = 0;

  /** 长度字段长度（4 字节） */
  private static final int LENGTH_FIELD_LENGTH = 4;

  /** 长度调整值（Payload 在 Length 字段之后紧跟） */
  private static final int LENGTH_ADJUSTMENT = 0;

  /** 跳过的初始字节数（不跳过长度字段本身，交给后续解码器处理） */
  private static final int INITIAL_BYTES_TO_STRIP = 0;

  /** 使用默认最大帧长度构造解码器。 */
  public LengthFieldFrameDecoder() {
    super(
        DEFAULT_MAX_FRAME_LENGTH,
        LENGTH_FIELD_OFFSET,
        LENGTH_FIELD_LENGTH,
        LENGTH_ADJUSTMENT,
        INITIAL_BYTES_TO_STRIP);
  }

  /**
   * 使用自定义最大帧长度构造解码器。
   *
   * @param maxFrameLength 最大帧长度（字节）
   */
  public LengthFieldFrameDecoder(int maxFrameLength) {
    super(
        maxFrameLength,
        LENGTH_FIELD_OFFSET,
        LENGTH_FIELD_LENGTH,
        LENGTH_ADJUSTMENT,
        INITIAL_BYTES_TO_STRIP);
  }
}
