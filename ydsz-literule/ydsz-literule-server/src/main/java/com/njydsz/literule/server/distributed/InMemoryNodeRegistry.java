package com.njydsz.literule.server.distributed;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 基于内存的节点注册表（P2-16 分布式执行）
 *
 * <p>适用于单节点部署或开发/测试环境。生产环境应使用 Redis 等分布式注册表。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
public class InMemoryNodeRegistry implements NodeRegistry {

  /** 心跳超时时间（毫秒，默认 30 秒） */
  private static final long DEFAULT_HEARTBEAT_TIMEOUT_MS = 30_000L;

  /** 节点表：nodeId → ClusterNode */
  private final Map<String, ClusterNode> nodes = new ConcurrentHashMap<>();

  /** 当前节点 ID */
  private final String selfNodeId;

  /** 心跳超时 */
  private final long heartbeatTimeoutMs;

  public InMemoryNodeRegistry(String selfNodeId) {
    this(selfNodeId, DEFAULT_HEARTBEAT_TIMEOUT_MS);
  }

  public InMemoryNodeRegistry(String selfNodeId, long heartbeatTimeoutMs) {
    this.selfNodeId = selfNodeId;
    this.heartbeatTimeoutMs = heartbeatTimeoutMs;
  }

  @Override
  public void register(ClusterNode node) {
    if (node == null || node.getNodeId() == null) {
      return;
    }
    node.setRegisteredAt(System.currentTimeMillis());
    node.setLastHeartbeatAt(System.currentTimeMillis());
    nodes.put(node.getNodeId(), node);
  }

  @Override
  public void unregister(String nodeId) {
    nodes.remove(nodeId);
  }

  @Override
  public void heartbeat(String nodeId) {
    ClusterNode node = nodes.get(nodeId);
    if (node != null) {
      node.setLastHeartbeatAt(System.currentTimeMillis());
    }
  }

  @Override
  public List<ClusterNode> getAliveNodes() {
    long now = System.currentTimeMillis();
    return nodes.values().stream()
        .filter(n -> n.isAlive(now, heartbeatTimeoutMs))
        .sorted(
            (a, b) -> {
              if (a.getNodeId() == null) return -1;
              return a.getNodeId().compareTo(b.getNodeId());
            })
        .collect(Collectors.toList());
  }

  @Override
  public String getSelfNodeId() {
    return selfNodeId;
  }

  /** 清理过期节点（心跳超时） */
  public int evictDeadNodes() {
    long now = System.currentTimeMillis();
    int evicted = 0;
    for (Map.Entry<String, ClusterNode> e : new ArrayList<>(nodes.entrySet())) {
      if (!e.getValue().isAlive(now, heartbeatTimeoutMs)) {
        nodes.remove(e.getKey());
        evicted++;
      }
    }
    return evicted;
  }
}
