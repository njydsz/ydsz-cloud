package com.njydsz.cronjob.server.core.dispatch;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import com.njydsz.cronjob.domain.entity.job.JobNode;
import com.njydsz.cronjob.server.config.CronjobProperties;
import com.njydsz.cronjob.server.core.discovery.NodeDiscoveryStrategy;
import com.njydsz.cronjob.server.core.executor.JobNodeHeartbeat;

/**
 * P0-1: Worker 节点选择器（调度器-执行器分离）。
 *
 * <p>当调度器-执行器分离模式启用时，Leader 节点通过本选择器选定 Worker 节点， 将非分片任务远程派发到 Worker 执行。
 *
 * <h3>选择策略</h3>
 *
 * <ul>
 *   <li>{@code round_robin}（默认）：轮询在线节点列表，均匀分配任务
 *   <li>{@code least_load}：选择当前运行任务数最少的节点（基于 JobNode.runningCount）
 * </ul>
 *
 * <h3>容错</h3>
 *
 * <ul>
 *   <li>无在线 Worker 节点时返回 null，调用方降级为 Leader 本地执行
 *   <li>仅 Leader 自身在线时返回 null（不向自己派发）
 *   <li>排除 Leader 节点，确保任务分散到 Worker
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
public class WorkerNodeSelector {

  private final CronjobProperties cronjobProperties;
  private final ObjectProvider<NodeDiscoveryStrategy> nodeDiscoveryStrategyProvider;
  private final ObjectProvider<JobNodeHeartbeat> heartbeatProvider;

  /** 轮询计数器（round_robin 策略使用） */
  private final AtomicInteger roundRobinCounter = new AtomicInteger(0);

  public WorkerNodeSelector(
      CronjobProperties cronjobProperties,
      ObjectProvider<NodeDiscoveryStrategy> nodeDiscoveryStrategyProvider,
      ObjectProvider<JobNodeHeartbeat> heartbeatProvider) {
    this.cronjobProperties = cronjobProperties;
    this.nodeDiscoveryStrategyProvider = nodeDiscoveryStrategyProvider;
    this.heartbeatProvider = heartbeatProvider;
  }

  /**
   * 选择一个 Worker 节点用于执行任务。
   *
   * <p>排除 Leader 节点（当前节点），仅从 Worker 节点中选择。
   *
   * @return 选中的 Worker 节点；无可用 Worker 时返回 null
   */
  public JobNode selectWorker() {
    List<JobNode> onlineNodes = getOnlineNodes();
    if (onlineNodes.isEmpty()) {
      log.debug("[WorkerSelector] 无在线节点");
      return null;
    }

    String localNodeId = resolveLocalNodeId();
    // 排除 Leader 节点
    List<JobNode> workers =
        onlineNodes.stream().filter(n -> !n.getNodeId().equals(localNodeId)).toList();

    if (workers.isEmpty()) {
      log.debug("[WorkerSelector] 无可用 Worker 节点(仅 Leader 在线)");
      return null;
    }

    String strategy =
        cronjobProperties.getSchedulerExecutorSeparation().getWorkerSelectionStrategy();
    if ("least_load".equalsIgnoreCase(strategy)) {
      return selectLeastLoad(workers);
    }
    // 默认 round_robin
    return selectRoundRobin(workers);
  }

  /**
   * 轮询选择 Worker 节点。
   *
   * @param workers 可用 Worker 列表
   * @return 选中的 Worker 节点
   */
  private JobNode selectRoundRobin(List<JobNode> workers) {
    int idx = Math.abs(roundRobinCounter.getAndIncrement()) % workers.size();
    return workers.get(idx);
  }

  /**
   * 最小负载选择 Worker 节点。
   *
   * <p>选择 runningCount 最小的节点；runningCount 相同时按 nodeId 升序（保证确定性）。
   *
   * @param workers 可用 Worker 列表
   * @return 选中的 Worker 节点
   */
  private JobNode selectLeastLoad(List<JobNode> workers) {
    return workers.stream()
        .min(
            (a, b) -> {
              int loadA = a.getRunningCount() != null ? a.getRunningCount() : 0;
              int loadB = b.getRunningCount() != null ? b.getRunningCount() : 0;
              int cmp = Integer.compare(loadA, loadB);
              return cmp != 0 ? cmp : a.getNodeId().compareTo(b.getNodeId());
            })
        .orElse(workers.get(0));
  }

  /** 获取在线节点列表。 */
  private List<JobNode> getOnlineNodes() {
    NodeDiscoveryStrategy strategy = nodeDiscoveryStrategyProvider.getIfAvailable();
    if (strategy != null) {
      return strategy.getOnlineNodes();
    }
    return Collections.emptyList();
  }

  /** 解析当前节点 ID。 */
  private String resolveLocalNodeId() {
    NodeDiscoveryStrategy strategy = nodeDiscoveryStrategyProvider.getIfAvailable();
    if (strategy != null) {
      return strategy.getLocalNodeId();
    }
    JobNodeHeartbeat heartbeat = heartbeatProvider.getIfAvailable();
    return heartbeat != null ? heartbeat.getNodeId() : null;
  }
}
