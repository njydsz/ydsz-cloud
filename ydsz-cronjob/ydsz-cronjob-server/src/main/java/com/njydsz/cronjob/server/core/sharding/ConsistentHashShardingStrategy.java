package com.njydsz.cronjob.server.core.sharding;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

/**
 * 一致性哈希分片策略（P1 增强，对标 ElasticJob 的一致性哈希分片）。
 *
 * <p>通过配置 {@code ydsz.cronjob.sharding.strategy=consistent_hash} 启用。
 *
 * <h3>与轮询（AverageShardingStrategy）的区别</h3>
 *
 * <p>轮询策略在节点列表变化时会全量重映射：节点 B 下线后，原本分配给 B 的分片会整体迁移到其他节点，
 * 无状态任务影响不大，但依赖本地缓存/局部性（如按租户维度分片后缓存热数据）的任务会大量缓存失效。
 *
 * <p>一致性哈希将节点映射到哈希环上（每个真实节点对应 160 个虚拟节点），分片按哈希值顺时针就近归属。
 * 节点增减时只影响环上邻近区段的分片（约 {@code 1/N} 比例），其余分片归属不变——最大程度保留缓存局部性。
 *
 * <h3>算法要点</h3>
 *
 * <ul>
 *   <li>哈希函数：FNV-1a 32 位变体（自研，避免引入第三方依赖，符合"最小化外部依赖"原则）
 *   <li>虚拟节点：每真实节点 160 个（默认值，与主流一致性哈希实现一致），降低节点分布不均概率
 *   <li>确定性：相同节点列表 + 分片数产生稳定分配，Leader 切换时分片不漂移
 * </ul>
 *
 * <p>注意：本策略与 {@link AverageShardingStrategy} 通过 {@code @ConditionalOnProperty} 互斥激活，
 * 保证容器内始终只有一个 {@link ShardingStrategy} Bean（DefaultTaskDispatcher 依赖单例解析）。
 *
 * @author ydsz-team
 * @since 1.2.0
 */
@Configuration
@ConditionalOnProperty(
    name = "ydsz.cronjob.sharding.strategy",
    havingValue = "consistent_hash")
public class ConsistentHashShardingStrategy implements ShardingStrategy {

  /** 每真实节点的虚拟节点数（与主流实现一致的默认值，平衡分布均匀性与内存开销） */
  private static final int VIRTUAL_NODE_COUNT = 160;

  @Override
  public List<ShardAssignment> assign(int shardTotal, List<String> onlineNodes) {
    if (shardTotal < 1) {
      throw new IllegalArgumentException("shardTotal 必须 >= 1, 实际: " + shardTotal);
    }
    if (onlineNodes == null || onlineNodes.isEmpty()) {
      throw new IllegalArgumentException("onlineNodes 不能为空");
    }
    // 构建哈希环（TreeMap 保证环上有序，ceilingEntry 实现顺时针查找）
    TreeMap<Integer, String> ring = new TreeMap<>();
    for (String node : onlineNodes) {
      for (int i = 0; i < VIRTUAL_NODE_COUNT; i++) {
        ring.put(hash(node + "#" + i), node);
      }
    }
    List<ShardAssignment> result = new ArrayList<>(shardTotal);
    for (int i = 0; i < shardTotal; i++) {
      String shardKey = "shard-" + i;
      int shardHash = hash(shardKey);
      Map.Entry<Integer, String> entry = ring.ceilingEntry(shardHash);
      if (entry == null) {
        // 超出环尾，回绕到环首（一致性哈希标准语义）
        entry = ring.firstEntry();
      }
      result.add(new ShardAssignment(entry.getValue(), i));
    }
    return Collections.unmodifiableList(result);
  }

  /**
   * FNV-1a 32 位哈希（返回非负值）。
   *
   * <p>FNV-1a 分布均匀、实现简单（无第三方依赖），满足一致性哈希对哈希函数"低碰撞 + 高分散"的要求。
   *
   * @param key 待哈希字符串
   * @return 非负 32 位哈希值
   */
  private int hash(String key) {
    int hash = 0x811c9dc5; // FNV offset basis
    for (byte b : key.getBytes(StandardCharsets.UTF_8)) {
      hash ^= (b & 0xff);
      hash *= 0x01000193; // FNV prime
    }
    return hash & 0x7fffffff;
  }
}
