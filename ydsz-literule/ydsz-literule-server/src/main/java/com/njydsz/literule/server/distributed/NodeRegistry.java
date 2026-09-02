package com.njydsz.literule.server.distributed;

import java.util.List;

/**
 * 集群节点注册表（P2-16 分布式执行）
 *
 * <p>提供节点注册、注销、心跳、查询存活节点列表等能力。
 * 生产环境使用 Redis 实现（{@link RedisNodeRegistry}），确保跨实例节点发现与心跳管理。
 *
 * @since 26.09.01
 * @author ydsz-team
 */
public interface NodeRegistry {

  /**
   * 注册当前节点到集群
   *
   * @param node 当前节点信息
   */
  void register(ClusterNode node);

  /**
   * 注销当前节点
   *
   * @param nodeId 节点 ID
   */
  void unregister(String nodeId);

  /**
   * 发送心跳（更新 lastHeartbeatAt）
   *
   * @param nodeId 节点 ID
   */
  void heartbeat(String nodeId);

  /**
   * 获取当前存活的所有节点列表（按 nodeId 排序）
   *
   * @return 存活节点列表
   */
  List<ClusterNode> getAliveNodes();

  /**
   * 获取当前节点 ID
   *
   * @return 当前实例的节点标识
   */
  String getSelfNodeId();

  /**
   * 获取集群节点总数
   *
   * @return 当前存活的集群节点总数
   */
  default int getClusterSize() {
    return getAliveNodes().size();
  }
}
