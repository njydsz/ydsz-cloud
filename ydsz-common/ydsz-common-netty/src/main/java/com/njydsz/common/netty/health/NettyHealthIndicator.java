package com.njydsz.common.netty.health;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;

import com.njydsz.common.netty.metric.NettyChannelMetrics;
import com.njydsz.common.netty.pool.NettyEventLoopPool;
import com.njydsz.common.netty.server.AbstractNettyServer;

/**
 * Netty 健康检查指标。
 *
 * <p>通过 Spring Boot Actuator 暴露 Netty Server 的运行状态：
 *
 * <ul>
 *   <li>Server 状态（running / stopped / port bound）
 *   <li>活跃连接数
 *   <li>Worker 线程池状态（引用计数）
 *   <li>SSL 状态（enabled / disabled）
 *   <li>累计连接/断开数
 *   <li>累计读写字节数
 * </ul>
 *
 * <p>当所有 Server 都正常运行时状态为 UP，任一 Server 未运行时状态为 DOWN。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class NettyHealthIndicator implements HealthIndicator {

  private final List<AbstractNettyServer> servers;
  private final NettyEventLoopPool eventLoopPool;
  private final NettyChannelMetrics metrics;

  /**
   * 构造 Netty 健康检查指标。
   *
   * @param servers Netty Server 列表（可为空）
   * @param eventLoopPool EventLoop 池（可为 null）
   * @param metrics 指标收集器（可为 null）
   */
  public NettyHealthIndicator(
      List<AbstractNettyServer> servers,
      NettyEventLoopPool eventLoopPool,
      NettyChannelMetrics metrics) {
    this.servers = servers != null ? servers : List.of();
    this.eventLoopPool = eventLoopPool;
    this.metrics = metrics;
  }

  @Override
  public Health health() {
    Health.Builder builder = Health.up();
    Map<String, Object> details = new LinkedHashMap<>();

    boolean allRunning = true;
    for (AbstractNettyServer server : servers) {
      String name = server.getClass().getSimpleName();
      boolean running = server.isRunning();
      if (!running) {
        allRunning = false;
      }
      Map<String, Object> serverDetail = new LinkedHashMap<>();
      serverDetail.put("running", running);
      serverDetail.put("port", server.getPort());
      serverDetail.put("activeChannels", server.getChannelGroupManager().globalSize());
      serverDetail.put("ssl", server.getProperties().getSsl().isEnabled());
      serverDetail.put("businessGroups", server.getChannelGroupManager().getGroupKeys());
      details.put(name, serverDetail);
    }

    if (!allRunning) {
      builder = Health.down();
    }

    // EventLoop 池状态
    if (eventLoopPool != null) {
      Map<String, Object> poolDetail = new LinkedHashMap<>();
      poolDetail.put("bossRefCount", eventLoopPool.getBossRefCount());
      poolDetail.put("workerRefCount", eventLoopPool.getWorkerRefCount());
      poolDetail.put("bossGroupActive", eventLoopPool.isBossGroupActive());
      poolDetail.put("workerGroupActive", eventLoopPool.isWorkerGroupActive());
      details.put("eventLoopPool", poolDetail);
    }

    // 指标摘要
    if (metrics != null) {
      Map<String, Object> metricsDetail = new LinkedHashMap<>();
      metricsDetail.put("activeChannels", metrics.getActiveChannels());
      metricsDetail.put("totalBytesRead", metrics.getTotalBytesRead());
      metricsDetail.put("totalBytesWritten", metrics.getTotalBytesWritten());
      details.put("metrics", metricsDetail);
    }

    builder.withDetails(details);
    return builder.build();
  }
}
