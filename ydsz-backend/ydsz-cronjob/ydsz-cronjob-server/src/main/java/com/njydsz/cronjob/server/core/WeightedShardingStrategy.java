package com.njydsz.cronjob.server.core.sharding;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

import com.njydsz.cronjob.domain.entity.job.JobNode;
import com.njydsz.cronjob.server.core.discovery.NodeDiscoveryStrategy;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 权重分片策略（P1-6 分片策略丰富化）。
 *
 * <p>根据节点的 CPU 使用率和运行任务数计算权重，将分片分配到负载较低的节点：
 * <ul>
 *   <li>权重 = (100 - cpuUsage) * (1 + 1/(runningCount+1))</li>
 *   <li>使用加权轮询算法，负载低的节点分配更多分片</li>
 * </ul>
 *
 * <p>启用方式：{@code ydsz.cronjob.sharding-strategy=weighted}
 *
 * @deprecated P2-C1: 建议使用 {@link AverageShardingStrategy} 或 {@link ConsistentHashShardingStrategy}。
 *     过多分片策略增加维护成本且实际生产使用率低，对标 XXL-Job 仅保留 2 种。
 */
@Deprecated(since = "1.5.0", forRemoval = true)
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(name = "ydsz.cronjob.sharding-strategy", havingValue = "weighted")
public class WeightedShardingStrategy implements ShardingStrategy {

    private final NodeDiscoveryStrategy nodeDiscoveryStrategy;

    @Override
    public List<ShardAssignment> assign(int shardTotal, List<String> onlineNodes) {
        if (shardTotal < 1) {
            throw new IllegalArgumentException("shardTotal 必须 >= 1, 实际: " + shardTotal);
        }
        if (onlineNodes == null || onlineNodes.isEmpty()) {
            throw new IllegalArgumentException("onlineNodes 不能为空");
        }

        // 获取节点负载信息（一次性查询，构建 Map 避免 N+1 查找）
        Map<String, JobNode> nodeMap = new HashMap<>();
        List<JobNode> allNodes = nodeDiscoveryStrategy.getOnlineNodes();
        if (allNodes != null) {
            for (JobNode node : allNodes) {
                nodeMap.put(node.getNodeId(), node);
            }
        }
        TreeMap<String, Double> nodeWeights = new TreeMap<>();
        for (String nodeId : onlineNodes) {
            double weight = calculateNodeWeight(nodeId, nodeMap);
            nodeWeights.put(nodeId, weight);
        }

        // 加权分配：按权重比例分配分片
        double totalWeight = nodeWeights.values().stream().mapToDouble(w -> w).sum();
        List<ShardAssignment> result = new ArrayList<>(shardTotal);
        int assigned = 0;
        for (String nodeId : onlineNodes) {
            double weight = nodeWeights.getOrDefault(nodeId, 1.0);
            int shardsForNode = (int) Math.round(shardTotal * weight / totalWeight);
            shardsForNode = Math.min(shardsForNode, shardTotal - assigned);
            for (int i = 0; i < shardsForNode; i++) {
                result.add(new ShardAssignment(nodeId, assigned++));
            }
        }
        // 分配剩余分片（取整误差）
        while (assigned < shardTotal) {
            // 分配给权重最高的节点
            String maxNode = nodeWeights.entrySet().stream()
                    .max((e1, e2) -> Double.compare(e1.getValue(), e2.getValue()))
                    .map(e -> e.getKey())
                    .orElse(onlineNodes.get(0));
            result.add(new ShardAssignment(maxNode, assigned++));
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * 计算节点权重（负载越低权重越高）。
     */
    private double calculateNodeWeight(String nodeId, Map<String, JobNode> nodeMap) {
        JobNode node = nodeMap.get(nodeId);
        if (node != null) {
            double cpuUsage = node.getCpuUsage() != null ? node.getCpuUsage().doubleValue() : 50.0;
            int runningCount = node.getRunningCount() != null ? node.getRunningCount() : 0;
            // 权重 = (100 - cpuUsage) * (1 + 1/(runningCount+1))
            // CPU 低 + 运行少 = 高权重
            double cpuFactor = Math.max(1.0, 100.0 - cpuUsage);
            double loadFactor = 1.0 + 1.0 / (runningCount + 1);
            return cpuFactor * loadFactor;
        }
        // 节点不在列表中，给默认权重
        return 1.0;
    }
}
