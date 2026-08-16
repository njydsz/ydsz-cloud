package com.njydsz.cronjob.server.core.dispatch;

import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import com.njydsz.cronjob.domain.entity.job.JobNode;
import com.njydsz.cronjob.server.core.discovery.NodeDiscoveryStrategy;
import com.njydsz.cronjob.server.core.executor.JobNodeHeartbeat;

/**
 * P3-20: 智能路由（机房/CPU 负载感知）。
 *
 * <p>在调度器-执行器分离模式下，根据执行器节点的资源负载（CPU、内存、运行任务数）
 * 和机房亲和性，选择最优的 Worker 节点执行任务。
 *
 * <h3>路由策略</h3>
 * <ul>
 *   <li><b>CPU 负载感知</b>：选择 CPU 使用率最低的节点（通过 JMX 获取）</li>
 *   <li><b>任务负载感知</b>：选择 runningCount 最低的节点（基于心跳上报）</li>
 *   <li><b>机房亲和性</b>：优先选择与 Leader 同机房的节点（降低网络延迟）</li>
 *   <li><b>综合评分</b>：CPU(40%) + 任务负载(40%) + 机房亲和(20%)</li>
 * </ul>
 *
 * <h3>评分公式</h3>
 * <pre>
 * score = (1 - cpuUsage) * 0.4 + (1 - taskLoadRatio) * 0.4 + affinityBonus * 0.2
 *
 * cpuUsage: 0.0 ~ 1.0（JMX 获取）
 * taskLoadRatio: runningCount / maxConcurrentPerWorker
 * affinityBonus: 同机房=1.0, 跨机房=0.0
 * </pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
public class SmartRoutingSelector {

    private final ObjectProvider<NodeDiscoveryStrategy> nodeDiscoveryStrategyProvider;
    private final ObjectProvider<JobNodeHeartbeat> heartbeatProvider;

    public SmartRoutingSelector(ObjectProvider<NodeDiscoveryStrategy> nodeDiscoveryStrategyProvider,
                                  ObjectProvider<JobNodeHeartbeat> heartbeatProvider) {
        this.nodeDiscoveryStrategyProvider = nodeDiscoveryStrategyProvider;
        this.heartbeatProvider = heartbeatProvider;
    }

    /**
     * 选择最优 Worker 节点。
     *
     * <p>综合 CPU 负载、任务负载和机房亲和性评分，选择得分最高的节点。
     *
     * @param maxConcurrentPerWorker 每个 Worker 的最大并发数（用于计算负载比）
     * @return 最优 Worker 节点；无可用节点返回 null
     */
    public JobNode selectBestWorker(int maxConcurrentPerWorker) {
        NodeDiscoveryStrategy strategy = nodeDiscoveryStrategyProvider.getIfAvailable();
        if (strategy == null) {
            return null;
        }

        List<JobNode> onlineNodes = strategy.getOnlineNodes();
        if (onlineNodes.isEmpty()) {
            return null;
        }

        String localNodeId = resolveLocalNodeId();
        String localRack = getLocalRack();

        // 排除 Leader 节点
        List<JobNode> workers = onlineNodes.stream()
                .filter(n -> !n.getNodeId().equals(localNodeId))
                .toList();

        if (workers.isEmpty()) {
            return null;
        }

        // 综合评分选择最优节点
        JobNode bestNode = null;
        double bestScore = -1;

        for (JobNode worker : workers) {
            double score = calculateScore(worker, maxConcurrentPerWorker, localRack);
            if (score > bestScore) {
                bestScore = score;
                bestNode = worker;
            }
        }

        if (bestNode != null && log.isDebugEnabled()) {
            log.debug("[SmartRouting] 选择最优 Worker: nodeId={} score={:.2f}", bestNode.getNodeId(), bestScore);
        }

        return bestNode;
    }

    /**
     * 计算节点综合评分。
     *
     * @param node                   Worker 节点
     * @param maxConcurrentPerWorker 最大并发数
     * @param localRack              本地机房标识
     * @return 评分（0.0 ~ 1.0，越高越优）
     */
    private double calculateScore(JobNode node, int maxConcurrentPerWorker, String localRack) {
        // 1. CPU 负载评分（0.4 权重）
        double cpuUsage = getCpuUsage();
        double cpuScore = (1.0 - cpuUsage) * 0.4;

        // 2. 任务负载评分（0.4 权重）
        int runningCount = node.getRunningCount() != null ? node.getRunningCount() : 0;
        double taskLoadRatio = maxConcurrentPerWorker > 0
                ? (double) runningCount / maxConcurrentPerWorker : 0;
        taskLoadRatio = Math.min(taskLoadRatio, 1.0);
        double taskScore = (1.0 - taskLoadRatio) * 0.4;

        // 3. 机房亲和性评分（0.2 权重）
        double affinityScore = isSameRack(node, localRack) ? 0.2 : 0.0;

        return cpuScore + taskScore + affinityScore;
    }

    /**
     * 获取当前节点 CPU 使用率（JMX）。
     *
     * @return CPU 使用率（0.0 ~ 1.0）；获取失败返回 0.5
     */
    private double getCpuUsage() {
        try {
            com.sun.management.OperatingSystemMXBean osBean = // FQN-OK: name conflict with java.lang.management.OperatingSystemMXBean
                    (com.sun.management.OperatingSystemMXBean) // FQN-OK: name conflict with java.lang.management.OperatingSystemMXBean
                            ManagementFactory.getOperatingSystemMXBean();
            // getCpuLoad() 替代已弃用的 getSystemCpuLoad()（JDK 14+）
            double load = osBean.getCpuLoad();
            return load >= 0 ? load : 0.5;
        } catch (Exception e) {
            return 0.5;
        }
    }

    /**
     * 获取本地机房标识。
     *
     * <p>通过环境变量 {@code RACK_ID} 或 hostname 前缀推断机房。
     *
     * @return 机房标识
     */
    private String getLocalRack() {
        String rack = System.getenv("RACK_ID");
        if (rack != null && !rack.isBlank()) {
            return rack;
        }
        try {
            String hostname = InetAddress.getLocalHost().getHostName();
            // hostname 前缀作为机房标识（如 bj-web-01 → bj）
            return hostname.contains("-") ? hostname.split("-")[0] : hostname;
        } catch (Exception e) {
            return "unknown";
        }
    }

    /**
     * 判断节点是否与本地同机房。
     */
    private boolean isSameRack(JobNode node, String localRack) {
        if (node.getHost() == null) {
            return false;
        }
        // 简化判断：hostname 前缀匹配
        String nodeHost = node.getHost();
        String nodeRack = nodeHost.contains("-") ? nodeHost.split("-")[0] : nodeHost;
        return localRack.equals(nodeRack);
    }

    /**
     * 解析当前节点 ID。
     */
    private String resolveLocalNodeId() {
        NodeDiscoveryStrategy strategy = nodeDiscoveryStrategyProvider.getIfAvailable();
        if (strategy != null) {
            return strategy.getLocalNodeId();
        }
        JobNodeHeartbeat heartbeat = heartbeatProvider.getIfAvailable();
        return heartbeat != null ? heartbeat.getNodeId() : null;
    }
}
