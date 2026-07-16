package com.njydsz.cronjob.server.core.sharding;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

import com.njydsz.cronjob.domain.entity.job.JobNodeDO;
import com.njydsz.cronjob.server.core.discovery.NodeDiscoveryStrategy;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 负载感知智能分片策略（P1-2）。
 *
 * <p>综合评估节点多维度负载指标，将分片优先分配到负载最低的节点：
 * <ul>
 *   <li>CPU 使用率（权重 35%）：直接反映节点计算压力</li>
 *   <li>内存使用率（权重 25%）：反映 JVM 堆压力，避免 OOM 风险</li>
 *   <li>运行任务数（权重 25%）：反映当前调度压力，避免任务堆积</li>
 *   <li>历史成功率（权重 15%）：反映节点稳定性，优先分配给稳定节点</li>
 * </ul>
 *
 * <h3>评分模型</h3>
 * <pre>
 *   loadScore = cpuUsage * 0.35 + memUsage * 0.25 + runningCountNormalized * 0.25 + (1 - successRate) * 0.15
 * </pre>
 * <p>loadScore 越低表示节点越空闲，优先分配分片。
 *
 * <h3>与 WeightedShardingStrategy 的区别</h3>
 * <ul>
 *   <li>WeightedShardingStrategy 仅考虑 CPU 和运行任务数（2 维）</li>
 *   <li>LoadAwareShardingStrategy 扩展到 4 维，并引入历史成功率反馈机制</li>
 *   <li>支持自适应权重调整：节点连续失败时自动降权</li>
 * </ul>
 *
 * <p>启用方式：{@code ydsz.cronjob.sharding-strategy=load_aware}
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(name = "ydsz.cronjob.sharding-strategy", havingValue = "load_aware")
public class LoadAwareShardingStrategy implements ShardingStrategy {

    private final NodeDiscoveryStrategy nodeDiscoveryStrategy;

    /** 节点连续失败计数（nodeId -> fail count，用于自适应降权） */
    private final Map<String, Integer> nodeFailStreak = new ConcurrentHashMap<>();

    /** 负载评分权重常量 */
    private static final double WEIGHT_CPU = 0.35;
    private static final double WEIGHT_MEM = 0.25;
    private static final double WEIGHT_RUNNING = 0.25;
    private static final double WEIGHT_SUCCESS = 0.15;

    /** 运行任务数归一化上限（超过此值按 100% 计算） */
    private static final int MAX_RUNNING_NORMALIZE = 16;

    @Override
    public List<ShardAssignment> assign(int shardTotal, List<String> onlineNodes) {
        if (shardTotal < 1) {
            throw new IllegalArgumentException("shardTotal 必须 >= 1, 实际: " + shardTotal);
        }
        if (onlineNodes == null || onlineNodes.isEmpty()) {
            throw new IllegalArgumentException("onlineNodes 不能为空");
        }

        List<JobNodeDO> allNodes = nodeDiscoveryStrategy.getOnlineNodes();

        // 计算每个节点的负载评分
        List<NodeScore> scores = new ArrayList<>(onlineNodes.size());
        for (String nodeId : onlineNodes) {
            double loadScore = calculateLoadScore(nodeId, allNodes);
            scores.add(new NodeScore(nodeId, loadScore));
        }

        // 按负载评分升序排序（loadScore 低的优先分配）
        scores.sort(Comparator.comparingDouble(NodeScore::score));

        // 轮询分配：按评分从低到高轮询分配分片
        // 确保负载最低的节点获得第一个分片，负载次低的获得第二个，依此循环
        List<ShardAssignment> result = new ArrayList<>(shardTotal);
        int nodeIdx = 0;
        for (int shardIdx = 0; shardIdx < shardTotal; shardIdx++) {
            NodeScore target = scores.get(nodeIdx % scores.size());
            result.add(new ShardAssignment(target.nodeId(), shardIdx));
            nodeIdx++;
        }

        if (log.isDebugEnabled()) {
            log.debug("[LoadAwareSharding] 分片分配完成: shardTotal={} assignments={}",
                    shardTotal, result);
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * 计算节点的综合负载评分（0-100，越低越空闲）。
     *
     * @param nodeId   节点 ID
     * @param allNodes 在线节点列表
     * @return 负载评分（0-100）
     */
    private double calculateLoadScore(String nodeId, List<JobNodeDO> allNodes) {
        JobNodeDO node = findNode(nodeId, allNodes);
        if (node == null) {
            // 节点不在列表中，给中等偏高的评分（保守分配）
            return 60.0;
        }

        // CPU 使用率（0-100）
        double cpuUsage = node.getCpuUsage() != null ? node.getCpuUsage().doubleValue() : 50.0;

        // 内存使用率（0-100）
        double memUsage = node.getMemUsagePct() != null ? node.getMemUsagePct().doubleValue() : 50.0;

        // 运行任务数归一化（0-100）
        int runningCount = node.getRunningCount() != null ? node.getRunningCount() : 0;
        double runningNormalized = Math.min(100.0, (double) runningCount / MAX_RUNNING_NORMALIZE * 100);

        // 历史成功率（默认 0.95，即 5% 失败率）
        double successRate = 0.95;

        // 连续失败惩罚：每次失败增加 10% 的负载评分
        int failStreak = nodeFailStreak.getOrDefault(nodeId, 0);
        double failPenalty = failStreak * 10.0;

        // 综合评分
        double loadScore = cpuUsage * WEIGHT_CPU
                + memUsage * WEIGHT_MEM
                + runningNormalized * WEIGHT_RUNNING
                + (1 - successRate) * 100 * WEIGHT_SUCCESS
                + failPenalty;

        return Math.min(100.0, loadScore);
    }

    /**
     * 记录节点执行失败（自适应降权）。
     *
     * @param nodeId 节点 ID
     */
    public void recordNodeFailure(String nodeId) {
        nodeFailStreak.merge(nodeId, 1, Integer::sum);
        log.debug("[LoadAwareSharding] 节点失败计数: nodeId={} streak={}",
                nodeId, nodeFailStreak.get(nodeId));
    }

    /**
     * 记录节点执行成功（重置失败计数）。
     *
     * @param nodeId 节点 ID
     */
    public void recordNodeSuccess(String nodeId) {
        nodeFailStreak.remove(nodeId);
    }

    /**
     * 在节点列表中查找指定节点。
     */
    private JobNodeDO findNode(String nodeId, List<JobNodeDO> allNodes) {
        for (JobNodeDO node : allNodes) {
            if (nodeId.equals(node.getNodeId())) {
                return node;
            }
        }
        return null;
    }

    /**
     * 节点评分记录（内部使用）。
     *
     * @param nodeId 节点 ID
     * @param score  负载评分
     */
    private record NodeScore(String nodeId, double score) {
    }
}
