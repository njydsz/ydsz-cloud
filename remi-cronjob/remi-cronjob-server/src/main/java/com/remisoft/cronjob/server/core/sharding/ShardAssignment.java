package com.remisoft.cronjob.server.core.sharding;

/**
 * 分片分配结果（P3 阶段引入）。
 *
 * <p>表示某个分片被分配到指定的执行节点。由 {@link ShardingStrategy} 计算产生，
 * 供 {@code DefaultTaskDispatcher} 决定每个分片在哪个节点上执行。
 *
 * @param nodeId    执行节点 ID（对应 remi_job_node.node_id）
 * @param shardIndex 分片索引（0-based）
 * @author remi-team
 * @since 1.0.0
 */
public record ShardAssignment(String nodeId, int shardIndex) {
}
