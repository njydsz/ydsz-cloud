package com.njydsz.common.netty.diagnostics;

import com.njydsz.common.netty.config.NettyProperties;
import com.njydsz.common.netty.metric.NettyChannelMetrics;
import com.njydsz.common.netty.pool.NettyEventLoopPool;
import com.njydsz.common.netty.transport.NativeTransportDetector.TransportType;
import io.netty.util.internal.PlatformDependent;
import lombok.extern.slf4j.Slf4j;

/**
 * Netty 启动环境检测报告器。
 *
 * <p>在 Server 启动成功后打印一份诊断报告，包含运行环境、传输模式、内存配置、 安全策略等关键信息，便于快速确认启动结果是否符合预期。
 *
 * <p>诊断报告覆盖：
 *
 * <ul>
 *   <li>运行环境：OS + JVM 版本
 *   <li>传输模式：Epoll / KQueue / NIO 自动选择结果
 *   <li>线程模型：Worker 线程数（自动或自定义）
 *   <li>内存配置：直接内存限制 + 当前使用量 + 池化策略
 *   <li>安全策略：SSL 状态 + 认证模式
 *   <li>连接控制：最大连接数 + Traffic Shaping
 *   <li>绑定信息：端口 + SO_BACKLOG + EventLoop 共享模式
 * </ul>
 *
 * <p>此报告器为纯工具类，不依赖 Spring 上下文，可在任何 Netty Server 启动场景使用。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see NettyPipelineDiagnostics
 */
@Slf4j
public final class NettyStartupReporter {

  /** 私有构造 — 工具类禁止实例化 */
  private NettyStartupReporter() {}

  /**
   * 输出 Netty Server 启动环境检测报告。
   *
   * @param pool EventLoop 线程池
   * @param properties Netty 配置属性
   * @param port 实际监听端口
   * @param metrics 指标收集器（可为 {@code null}）
   */
  public static void report(
      NettyEventLoopPool pool, NettyProperties properties, int port, NettyChannelMetrics metrics) {
    StringBuilder sb = new StringBuilder(800);
    sb.append("\n");
    sb.append("╔══════════════════════════════════════════════════════════╗\n");
    sb.append("║               YDSZ Netty Server Startup Report           ║\n");
    sb.append("╠══════════════════════════════════════════════════════════╣\n");

    // 运行环境
    sb.append(String.format("║ OS:          %-43s ║%n", System.getProperty("os.name") + " " + System.getProperty("os.version")));
    sb.append(String.format("║ JVM:         %-43s ║%n", System.getProperty("java.version")));
    sb.append(String.format("║ Arch:        %-43s ║%n", System.getProperty("os.arch")));
    sb.append("╠══════════════════════════════════════════════════════════╣\n");

    // 传输模式
    TransportType transport = pool.getTransportType();
    sb.append(String.format("║ Transport:   %-43s ║%n", transport));
    sb.append(String.format("║ Workers:     %-43s ║%n", formatWorkerThreads(properties)));
    sb.append(String.format("║ Boss:        %-43s ║%n", properties.getBossThreads()));
    sb.append(String.format("║ SharedLoop:  %-43s ║%n", properties.isSharedEventLoop()));
    sb.append("╠══════════════════════════════════════════════════════════╣\n");

    // 内存配置
    sb.append(String.format("║ DirectMax:   %-43s ║%n", formatMemorySize(maxDirectMemory())));
    sb.append(String.format("║ DirectUsed:  %-43s ║%n", formatMemorySize(usedDirectMemory())));
    sb.append(String.format("║ Pooled:      %-43s ║%n", properties.getAllocator().isPooled()));
    sb.append(String.format("║ PreferHeap:  %-43s ║%n", !properties.getAllocator().isPreferDirect()));
    sb.append("╠══════════════════════════════════════════════════════════╣\n");

    // 安全与连接
    sb.append(String.format("║ SSL:         %-43s ║%n", properties.getSsl().isEnabled()));
    sb.append(String.format("║ ClientAuth:  %-43s ║%n", properties.getSsl().isNeedClientAuth()));
    sb.append(String.format("║ MaxConn:     %-43s ║%n", properties.getConnectionControl().getMaxConnections() <= 0 ? "unlimited" : properties.getConnectionControl().getMaxConnections()));
    sb.append(String.format("║ Backlog:     %-43s ║%n", properties.getSoBacklog()));
    sb.append(String.format("║ Port:        %-43s ║%n", port));
    sb.append("╠══════════════════════════════════════════════════════════╣\n");

    // 心跳与重连
    sb.append(String.format("║ ReaderIdle:  %-43s ║%n", properties.getIdle().getReaderIdleSeconds() + "s"));
    sb.append(String.format("║ WriterIdle:  %-43s ║%n", properties.getIdle().getWriterIdleSeconds() + "s"));
    sb.append(String.format("║ Reconnect:   %-43s ║%n", properties.getReconnect().isEnabled()));
    sb.append(String.format("║ Traffic:     %-43s ║%n", properties.getTrafficShaping().isEnabled()));
    sb.append("╠══════════════════════════════════════════════════════════╣\n");

    // 直接内存使用率告警
    double usageRatio = calculateUsageRatio();
    if (usageRatio >= 0.8) {
      sb.append(String.format("║ ⚠ WARN: Direct memory usage %.1f%% - approaching limit  ║%n", usageRatio * 100));
    } else {
      sb.append(String.format("║ DirectUsage: %-43s ║%n", String.format("%.1f%%", usageRatio * 100)));
    }

    // EventLoop 池引用计数
    sb.append(String.format("║ BossRef:     %-43s ║%n", pool.getBossRefCount()));
    sb.append(String.format("║ WorkerRef:   %-43s ║%n", pool.getWorkerRefCount()));

    // 指标收集状态
    sb.append(String.format("║ Metrics:     %-43s ║%n", metrics != null ? "enabled" : "no-op"));

    sb.append("╚══════════════════════════════════════════════════════════╝");

    log.info(sb.toString());
  }

  /**
   * 格式化 Worker 线程数显示（自动模式标注实际值）。
   *
   * @param properties Netty 配置
   * @return 线程数描述字符串
   */
  private static String formatWorkerThreads(NettyProperties properties) {
    if (properties.getWorkerThreads() <= 0) {
      return "auto (" + (Runtime.getRuntime().availableProcessors() * 2) + ")";
    }
    return String.valueOf(properties.getWorkerThreads());
  }

  /**
   * 获取最大直接内存限制（字节）。
   *
   * @return 最大直接内存字节数
   */
  private static long maxDirectMemory() {
    try {
      return PlatformDependent.maxDirectMemory();
    } catch (Throwable t) {
      return Runtime.getRuntime().maxMemory();
    }
  }

  /**
   * 获取当前直接内存使用量（字节）。
   *
   * @return 直接内存使用量字节数
   */
  private static long usedDirectMemory() {
    try {
      return PlatformDependent.usedDirectMemory();
    } catch (Throwable t) {
      return 0;
    }
  }

  /**
   * 计算直接内存使用率。
   *
   * @return 使用率 (0.0 - 1.0)，无法获取时返回 0.0
   */
  private static double calculateUsageRatio() {
    long max = maxDirectMemory();
    long used = usedDirectMemory();
    return max <= 0 ? 0.0 : (double) used / max;
  }

  /**
   * 将字节数格式化为人类可读字符串（KB / MB / GB）。
   *
   * @param bytes 字节数
   * @return 格式化字符串
   */
  private static String formatMemorySize(long bytes) {
    if (bytes <= 0) {
      return "N/A";
    }
    if (bytes >= 1024L * 1024 * 1024) {
      return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }
    if (bytes >= 1024 * 1024) {
      return String.format("%.2f MB", bytes / (1024.0 * 1024));
    }
    return String.format("%.2f KB", bytes / 1024.0);
  }
}
