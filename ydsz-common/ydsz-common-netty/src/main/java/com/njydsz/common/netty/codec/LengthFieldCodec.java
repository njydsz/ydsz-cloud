package com.njydsz.common.netty.codec;

import io.netty.channel.ChannelPipeline;
import io.netty.handler.codec.LengthFieldPrepender;

/**
 * Length Field 编解码器组合 — 一站式解决 TCP 粘包/半包问题。
 *
 * <p>组合 {@link LengthFieldFrameDecoder}（解码）和 {@link LengthFieldPrepender}（编码）， 业务方只需在 Pipeline
 * 中添加此组合即可完成长度域编解码。
 *
 * <p>协议格式：
 *
 * <pre>
 * +--------+------------------+
 * | Length  | Payload          |
 * | 4 bytes | Length bytes     |
 * +--------+------------------+
 * </pre>
 *
 * <p>使用方式：
 *
 * <pre>{@code
 * @Override
 * protected void initChannelPipeline(SocketChannel ch) {
 *     LengthFieldCodec.addToPipeline(ch.pipeline());
 *     ch.pipeline().addLast(new MyBusinessHandler());
 * }
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class LengthFieldCodec {

  /** 默认长度字段字节数（4 字节） */
  public static final int DEFAULT_LENGTH_FIELD_LENGTH = 4;

  /** 默认最大帧长度（1MB） */
  public static final int DEFAULT_MAX_FRAME_LENGTH = 1024 * 1024;

  private LengthFieldCodec() {}

  /**
   * 向 Pipeline 添加 Length Field 编解码器（使用默认配置）。
   *
   * @param pipeline Channel Pipeline
   */
  public static void addToPipeline(ChannelPipeline pipeline) {
    addToPipeline(pipeline, DEFAULT_MAX_FRAME_LENGTH, DEFAULT_LENGTH_FIELD_LENGTH);
  }

  /**
   * 向 Pipeline 添加 Length Field 编解码器。
   *
   * @param pipeline Channel Pipeline
   * @param maxFrameLength 最大帧长度
   * @param lengthFieldLength 长度字段字节数（2 或 4）
   */
  public static void addToPipeline(
      ChannelPipeline pipeline, int maxFrameLength, int lengthFieldLength) {
    pipeline.addLast("frameDecoder", new LengthFieldFrameDecoder(maxFrameLength));
    pipeline.addLast("frameEncoder", new LengthFieldPrepender(lengthFieldLength));
  }
}
