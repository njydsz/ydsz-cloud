package com.njydsz.common.netty.diagnostics;

import java.util.ArrayList;
import java.util.List;

import io.netty.channel.Channel;
import io.netty.channel.ChannelPipeline;
import lombok.extern.slf4j.Slf4j;

/**
 * Netty Pipeline 诊断工具。
 *
 * <p>提供运行时 Pipeline 快照和 Channel 状态查询能力，用于线上问题排查：
 *
 * <ul>
 *   <li>输出 Pipeline 中所有 Handler 名称及顺序
 *   <li>检测 Handler 是否存在
 *   <li>获取 Channel 基础信息（本地/远端地址、是否活跃、是否可写）
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
public class NettyPipelineDiagnostics {

  private NettyPipelineDiagnostics() {}

  /**
   * 获取 Pipeline 快照（Handler 名称列表）。
   *
   * @param channel 目标 Channel
   * @return Pipeline 中所有 Handler 名称列表（按顺序）
   */
  public static List<String> dumpPipeline(Channel channel) {
    List<String> handlers = new ArrayList<>(16);
    if (channel == null) {
      return handlers;
    }
    ChannelPipeline pipeline = channel.pipeline();
    pipeline
        .names()
        .forEach(
            name -> handlers.add(name + "(" + pipeline.get(name).getClass().getSimpleName() + ")"));
    return handlers;
  }

  /**
   * 获取 Pipeline 快照字符串（单行格式）。
   *
   * @param channel 目标 Channel
   * @return Pipeline 字符串，格式：handler1 -> handler2 -> ...
   */
  public static String dumpPipelineString(Channel channel) {
    if (channel == null) {
      return "null";
    }
    return String.join(" -> ", channel.pipeline().names());
  }

  /**
   * 检查 Pipeline 中是否包含指定名称的 Handler。
   *
   * @param channel 目标 Channel
   * @param handlerName Handler 名称
   * @return true 表示存在
   */
  public static boolean hasHandler(Channel channel, String handlerName) {
    if (channel == null) {
      return false;
    }
    return channel.pipeline().get(handlerName) != null;
  }

  /**
   * 获取 Channel 基础信息。
   *
   * @param channel 目标 Channel
   * @return Channel 信息字符串
   */
  public static String getChannelInfo(Channel channel) {
    if (channel == null) {
      return "Channel{null}";
    }
    return String.format(
        "Channel{id=%s, local=%s, remote=%s, active=%s, writable=%s, open=%s}",
        channel.id().asShortText(),
        channel.localAddress(),
        channel.remoteAddress(),
        channel.isActive(),
        channel.isWritable(),
        channel.isOpen());
  }

  /**
   * 打印 Channel 诊断日志（包含 Pipeline 和 Channel 基础信息）。
   *
   * @param channel 目标 Channel
   */
  public static void logDiagnostics(Channel channel) {
    if (channel == null) {
      log.info("[Netty-Diagnostics] Channel: null");
      return;
    }
    log.info("[Netty-Diagnostics] Channel: {}", getChannelInfo(channel));
    log.info("[Netty-Diagnostics] Pipeline: {}", dumpPipelineString(channel));
  }
}
