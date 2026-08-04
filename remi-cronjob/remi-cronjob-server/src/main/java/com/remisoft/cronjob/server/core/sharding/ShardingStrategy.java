package com.remisoft.cronjob.server.core.sharding;

import java.util.List;

/**
 * 分片策略接口（P3 阶段引入）。
 *
 * <p>根据在线节点列表和总分片数计算分片分配方案。对标 XXL-Job 的分片广播策略
 * 和 PowerJob 的 {@code InstanceContext} 分片机制。
 *
 * <h3>设计目标</h3>
 * <ul>
 *   <li>可插拔：业务侧可自定义实现替代默认的 {@link AverageShardingStrategy}</li>
 *   <li>确定性：相同输入（节点列表 + 分片数）应产生相同输出，保证 Leader 切换时分片稳定</li>
 *   <li>均衡：尽量让各节点承担相同数量的分片</li>
 * </ul>
 *
 * <p>典型实现：
 * <ul>
 *   <li>{@link AverageShardingStrategy}：轮询平均分配（默认）</li>
 *   <li>未来扩展：一致性哈希、按标签亲和性、按负载权重等</li>
 * </ul>
 *
 * @author remi-team
 * @since 1.0.0
 */
public interface ShardingStrategy {

    /**
     * 计算分片分配方案。
     *
     * @param shardTotal  总分片数（&gt;= 1）
     * @param onlineNodes 在线节点 ID 列表（已排序，保证确定性）
     * @return 分片分配列表；每个元素表示一个分片归属哪个节点
     * @throws IllegalArgumentException 当 shardTotal &lt; 1 或 onlineNodes 为空
     */
    List<ShardAssignment> assign(int shardTotal, List<String> onlineNodes);
}
