package com.njydsz.common.netty.endpoint;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.Selector;

import com.njydsz.common.netty.metric.NettyChannelMetrics;
import com.njydsz.common.netty.pool.NettyEventLoopPool;
import com.njydsz.common.netty.server.AbstractNettyServer;

/**
 * Netty Actuator 端点 — 暴露运行时诊断信息。
 *
 * <p>通过 {@code /actuator/netty} 端点查看：
 *
 * <ul>
 *   <li>Server 运行状态（端口、活跃通道数、SSL 状态）
 *   <li>EventLoop 池状态（引用计数、是否活跃）
 *   <li>指标摘要（累计连接/断开、读写字节数）
 * </ul>
 *
 * <p>通过 {@code /actuator/netty/{serverName}} 查看指定 Server 详情。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Endpoint(id = "netty")
@RequiredArgsConstructor
public class NettyActuatorEndpoint {

  private final List<AbstractNettyServer> servers;
  private final NettyEventLoopPool eventLoopPool;
  private final NettyChannelMetrics metrics;

  /**
   * 获取所有 Netty 组件的摘要信息。
   *
   * @return 诊断信息 Map
   */
  @ReadOperation
  public Map<String, Object> nettySummary() {
    Map<String, Object> result = new LinkedHashMap<>(16);

    // Server 状态
    Map<String, Object> serversDetail = new LinkedHashMap<>(16);
    for (AbstractNettyServer server : servers) {
      String name = server.getClass().getSimpleName();
      Map<String, Object> serverDetail = new LinkedHashMap<>(16);
      serverDetail.put("running", server.isRunning());
      serverDetail.put("port", server.getPort());
      serverDetail.put("activeChannels", server.getChannelGroupManager().globalSize());
      serverDetail.put("ssl", server.getProperties().getSsl().isEnabled());
      serversDetail.put(name, serverDetail);
    }
    result.put("servers", serversDetail);

    // EventLoop 池状态
    if (eventLoopPool != null) {
      Map<String, Object> poolDetail = new LinkedHashMap<>(16);
      poolDetail.put("bossRefCount", eventLoopPool.getBossRefCount());
      poolDetail.put("workerRefCount", eventLoopPool.getWorkerRefCount());
      poolDetail.put("bossGroupActive", eventLoopPool.isBossGroupActive());
      poolDetail.put("workerGroupActive", eventLoopPool.isWorkerGroupActive());
      poolDetail.put("transportType", eventLoopPool.getTransportType().name());
      result.put("eventLoopPool", poolDetail);
    }

    // 指标摘要
    if (metrics != null) {
      Map<String, Object> metricsDetail = new LinkedHashMap<>(16);
      metricsDetail.put("activeChannels", metrics.getActiveChannels());
      metricsDetail.put("totalBytesRead", metrics.getTotalBytesRead());
      metricsDetail.put("totalBytesWritten", metrics.getTotalBytesWritten());
      result.put("metrics", metricsDetail);
    }

    return result;
  }

  /**
   * 获取指定 Server 的详细信息。
   *
   * @param serverName Server 类名
   * @return Server 详情
   */
  @ReadOperation
  public Map<String, Object> serverDetail(@Selector String serverName) {
    Map<String, Object> detail = new LinkedHashMap<>(16);

    for (AbstractNettyServer server : servers) {
      if (server.getClass().getSimpleName().equals(serverName)) {
        detail.put("running", server.isRunning());
        detail.put("port", server.getPort());
        detail.put("activeChannels", server.getChannelGroupManager().globalSize());
        detail.put("businessGroups", server.getChannelGroupManager().getGroupKeys());
        detail.put("ssl", server.getProperties().getSsl().isEnabled());
        detail.put("sharedEventLoop", server.getProperties().isSharedEventLoop());
        return detail;
      }
    }

    detail.put("error", "Server not found: " + serverName);
    return detail;
  }
}
