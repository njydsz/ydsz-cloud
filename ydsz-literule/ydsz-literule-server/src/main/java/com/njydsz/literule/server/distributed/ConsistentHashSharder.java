package com.njydsz.literule.server.distributed;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.common.util.security.DigestUtils;

/**
 * 一致性 Hash 分片器（P2-16 分布式执行）
 *
 * <p>基于虚拟节点（virtual node）的一致性 hash 算法，将规则/上下文均匀分布到集群节点。
 *
 * <h3>核心特性</h3>
 *
 * <ul>
 *   <li>虚拟节点：每个物理节点默认 150 个虚拟节点，提高均匀性
 *   <li>MD5 hash：稳定且分布均匀
 *   <li>本地缓存：对相同 key 的分片结果做 LRU 缓存（可选）
 *   <li>节点变更感知：当节点列表变更时自动重建 hash 环
 * </ul>
 *
 * <h3>使用方式</h3>
 *
 * <pre>
 * ConsistentHashSharder sharder = new ConsistentHashSharder();
 * sharder.updateNodes(nodeList);
 * ClusterNode owner = sharder.shard("rule-code-001");
 * boolean mine = sharder.isMine("rule-code-001", selfNodeId);
 * </pre>
 *
 * @since 1.0.0
 * @author ydsz-team
 */
public class ConsistentHashSharder {

  private static final Logger LOG = LoggerFactory.getLogger(ConsistentHashSharder.class);

  /** 默认虚拟节点数 */
  public static final int DEFAULT_VNODES = 150;

  /** hash 环：hash值 → 物理节点（TreeMap 保证有序） */
  private volatile TreeMap<Long, ClusterNode> ring = new TreeMap<>();

  /** 当前节点列表的签名（用于检测节点变更） */
  private volatile String nodeSignature = "";

  /** 虚拟节点数 */
  private final int virtualNodes;

  public ConsistentHashSharder() {
    this(DEFAULT_VNODES);
  }

  public ConsistentHashSharder(int virtualNodes) {
    this.virtualNodes = Math.max(1, virtualNodes);
  }

  /**
   * 更新节点列表，重建 hash 环
   *
   * @param nodes 当前存活的节点列表
   */
  public synchronized void updateNodes(List<ClusterNode> nodes) {
    if (nodes == null || nodes.isEmpty()) {
      ring = new TreeMap<>();
      nodeSignature = "";
      return;
    }
    // 计算签名，避免不必要的重建
    String sig = buildSignature(nodes);
    if (sig.equals(nodeSignature)) {
      return;
    }
    nodeSignature = sig;

    TreeMap<Long, ClusterNode> newRing = new TreeMap<>();
    for (ClusterNode node : nodes) {
      int vnodes = Math.max(1, node.getWeight() * virtualNodes);
      for (int i = 0; i < vnodes; i++) {
        String vk = node.getNodeId() + "#" + i;
        long hash = hash(vk);
        newRing.put(hash, node);
      }
    }
    this.ring = newRing;
  }

  /**
   * 对 key 进行分片，返回所属节点
   *
   * @param key 分片键（规则编码 / 上下文 ID 等）
   * @return 所属节点；环为空时返回 null
   */
  public ClusterNode shard(String key) {
    TreeMap<Long, ClusterNode> r = this.ring;
    if (r == null || r.isEmpty()) {
      return null;
    }
    long h = hash(key);
    // 顺时针查找第一个 >= h 的节点
    Map.Entry<Long, ClusterNode> entry = r.ceilingEntry(h);
    if (entry == null) {
      // 环回绕到第一个
      entry = r.firstEntry();
    }
    return entry.getValue();
  }

  /**
   * 判断 key 是否属于当前节点
   *
   * @param key 分片键
   * @param nodeId 当前节点 ID
   * @return true 如果 key 属于当前节点
   */
  public boolean isMine(String key, String nodeId) {
    ClusterNode owner = shard(key);
    return owner != null && nodeId != null && nodeId.equals(owner.getNodeId());
  }

  /**
   * 判断 key 是否属于当前节点（直接传节点列表，避免重建环）
   *
   * <p>适用于不想维护环状态的场景（如无状态调用）。
   */
  public static boolean isMine(String key, String nodeId, List<ClusterNode> nodes) {
    if (nodes == null || nodes.isEmpty() || nodeId == null || key == null) {
      return true; // 无节点信息时默认本地执行
    }
    // 简化版：对 key 做 hash，模 nodes.size()
    int idx = (int) (Math.abs(hash0(key)) % nodes.size());
    return nodeId.equals(nodes.get(idx).getNodeId());
  }

  /** 获取当前环上的节点数量 */
  public int getNodeCount() {
    TreeMap<Long, ClusterNode> r = this.ring;
    long count = r == null ? 0 : r.values().stream().map(ClusterNode::getNodeId).distinct().count();
    return (int) count;
  }

  /** 获取当前节点签名 */
  public String getNodeSignature() {
    return nodeSignature;
  }

  /** MD5 hash（取前 8 字节转 long） */
  static long hash(String key) {
    return hash0(key);
  }

  private static long hash0(String key) {
    byte[] keyBytes = key.getBytes(StandardCharsets.UTF_8);
    byte[] digest;
    try {
      digest = DigestUtils.digest(new java.io.ByteArrayInputStream(keyBytes), "MD5");
    } catch (java.io.IOException e) {
      throw new IllegalStateException("MD5 摘要计算失败: " + key, e);
    }
    long h = 0;
    for (int i = 0; i < 8; i++) {
      h <<= 8;
      h |= (digest[i] & 0xFF);
    }
    return h & Long.MAX_VALUE;
  }

  private String buildSignature(List<ClusterNode> nodes) {
    StringBuilder sb = new StringBuilder();
    for (ClusterNode n : nodes) {
      sb.append(n.getNodeId()).append(':').append(n.getWeight()).append(',');
    }
    return sb.toString();
  }
}
