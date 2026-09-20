package com.njydsz.common.netty.endpoint;

import com.njydsz.common.netty.metric.NettyChannelMetrics;
import com.njydsz.common.netty.pool.NettyEventLoopPool;
import com.njydsz.common.netty.server.AbstractNettyServer;
import com.njydsz.common.netty.session.ConnectionSession;
import com.njydsz.common.netty.session.SessionRepository;
import io.netty.channel.Channel;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.annotation.Selector;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Netty Actuator 端点 — 暴露运行时诊断信息。
 *
 * <p>通过 {@code /actuator/netty} 端点查看：
 *
 * <ul>
 *   <li>Server 运行状态（端口、活跃通道数、SSL 状态）
 *   <li>EventLoop 池状态（引用计数、是否活跃）
 *   <li>指标摘要（累计连接/断开、读写字节数、直接内存使用率）
 *   <li>活跃 Session 数（框架管理的业务会话层）
 * </ul>
 *
 * <p>通过 {@code /actuator/netty/{serverName}} 查看指定 Server 详情。
 *
 * <p>通过 {@code /actuator/netty/channels} 和 {@code /actuator/netty/channels/{channelId}} 查看单个 Channel 级别的诊断信息（远端地址、Pipeline 结构等）。
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
      serverDetail.put("sharedEventLoop", server.getProperties().isSharedEventLoop());
      serverDetail.put("transportType", eventLoopPool.getTransportType().name());
      // Session 数（业务层会话）
      SessionRepository repo = server.getSessionRepository();
      serverDetail.put("sessionCount", repo != null ? repo.size() : 0);
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
      // 直接内存使用率
      metricsDetail.put("directMemoryUsed", metrics.getDirectMemoryUsed());
      metricsDetail.put("directMemoryUsageRatio", String.format("%.2f%%", metrics.getDirectMemoryUsageRatio() * 100));
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
        detail.put("maxConnections", server.getProperties().getConnectionControl().getMaxConnections());
        // Session 信息
        SessionRepository repo = server.getSessionRepository();
        detail.put("sessionCount", repo != null ? repo.size() : 0);
        return detail;
      }
    }

    detail.put("error", "Server not found: " + serverName);
    return detail;
  }

  /**
   * 获取所有活跃 Channel 的诊断信息。
   *
   * @return Channel 详情列表
   */
  @ReadOperation
  public Map<String, Object> channelDetails() {
    Map<String, Object> result = new LinkedHashMap<>(16);
    List<Map<String, Object>> channels = new ArrayList<>();

    for (AbstractNettyServer server : servers) {
      server.getChannelGroupManager().getGlobalGroup().forEach(ch -> {
        Map<String, Object> detail = new LinkedHashMap<>(16);
        detail.put("server", server.getClass().getSimpleName());
        detail.put("channelId", ch.id().asShortText());
        detail.put("remoteAddress", String.valueOf(ch.remoteAddress()));
        detail.put("localAddress", String.valueOf(ch.localAddress()));
        detail.put("isActive", ch.isActive());
        detail.put("isWritable", ch.isWritable());
        detail.put("isOpen", ch.isOpen());
        detail.put("pipeline", ch.pipeline().names());
        channels.add(detail);
      });
    }

    result.put("total", channels.size());
    result.put("channels", channels);
    return result;
  }

  /**
   * 获取指定 Channel 的完整诊断信息。
   *
   * @param channelId Channel 短 ID
   * @return Channel 详情
   */
  @ReadOperation
  public Map<String, Object> channelDetail(@Selector String channelId) {
    Map<String, Object> detail = new LinkedHashMap<>(16);

    for (AbstractNettyServer server : servers) {
      for (Channel ch : server.getChannelGroupManager().getGlobalGroup()) {
        if (ch.id().asShortText().equals(channelId)) {
          detail.put("server", server.getClass().getSimpleName());
          detail.put("channelId", ch.id().asShortText());
          detail.put("remoteAddress", String.valueOf(ch.remoteAddress()));
          detail.put("localAddress", String.valueOf(ch.localAddress()));
          detail.put("isActive", ch.isActive());
          detail.put("isWritable", ch.isWritable());
          detail.put("isOpen", ch.isOpen());
          detail.put("pipeline", ch.pipeline().names());
          detail.put("pipelineDetail", buildPipelineDetail(ch));
          // 关联的 Session 信息
          SessionRepository repo = server.getSessionRepository();
          if (repo != null) {
            repo.find(s -> s.getChannel().equals(ch)).forEach(session -> {
              detail.put("sessionId", session.getSessionId());
              detail.put("bizId", session.getBizId());
              detail.put("sessionState", session.getState().name());
              detail.put("connectedTime", session.getConnectedTime());
              detail.put("lastInteractionTime", session.getLastInteractionTime());
            });
          }
          return detail;
        }
      }
    }

    detail.put("error", "Channel not found: " + channelId);
    return detail;
  }

  /**
   * 构建 Pipeline 详细信息（Handler 名称 + 类名）。
   *
   * @param channel Channel
   * @return Pipeline 详情列表
   */
  private List<String> buildPipelineDetail(Channel channel) {
    List<String> handlers = new ArrayList<>(16);
    channel.pipeline().names().forEach(name ->
        handlers.add(name + "(" + channel.pipeline().get(name).getClass().getSimpleName() + ")"));
    return handlers;
  }
}
