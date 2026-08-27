package com.njydsz.cronjob.server.core.dispatch;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import com.njydsz.cronjob.domain.vo.JobNodeVO;
import com.njydsz.cronjob.server.config.CronjobProperties;
import com.njydsz.cronjob.server.core.discovery.NodeDiscoveryStrategy;
import com.njydsz.cronjob.server.core.executor.JobNodeHeartbeat;

/**
 * P0-1: Worker 节点选择器（调度器-执行器分离）。
 *
 * <p>当调度器-执行器分离模式启用时，Leader 节点通过本选择器选定 Worker 节点， 将非分片任务远程派发到 Worker 执行。
 *
 * <h3>选择策略（{@code ydsz.cronjob.scheduler-executor-separation.worker-selection-strategy}）</h3>
 *
 * <ul>
 *   <li>{@code round_robin}（默认）：轮询在线节点列表，均匀分配任务
 *   <li>{@code least_load}：选择当前运行任务数最少的节点（基于 JobNode.runningCount）
 *   <li>{@code random}：随机选择（无状态，适合大任务量下的近似均匀）
 *   <li>{@code consistent_hash}：按任务 routingKey（jobKey）FNV-1a 哈希稳定命中节点，
 *       同一任务在节点列表不变时恒定落在同一 Worker（有状态任务友好）
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

  /** FNV-1a 32 位哈希：偏移基数（offset basis） */
  private static final int FNV1A_OFFSET_BASIS = 0x811c9dc5;

  /** FNV-1a 32 位哈希：素数 */
  private static final int FNV1A_PRIME = 0x01000193;

  /** byte → 无符号整数掩码（0-255） */
  private static final int BYTE_UNSIGNED_MASK = 0xff;

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
  public JobNodeVO selectWorker() {
    return selectWorker(Collections.emptySet(), null);
  }

  /**
   * P2-1: 选择一个 Worker 节点用于执行任务（排除指定节点）。
   *
   * <p>用于远程派发失败时重试：排除已尝试过的 Worker 节点，从剩余节点中选择。
   *
   * @param excludedNodeIds 需要排除的节点 ID 集合（已尝试失败的节点）
   * @return 选中的 Worker 节点；无可用 Worker 时返回 null
   */
  public JobNodeVO selectWorker(Set<String> excludedNodeIds) {
    return selectWorker(excludedNodeIds, null);
  }

  /**
   * P1-F10: 选择一个 Worker 节点用于执行任务（排除指定节点 + 路由键）。
   *
   * <p>{@code consistent_hash} 策略依赖 routingKey（通常为 jobKey）做稳定哈希路由；
   * 其余策略忽略 routingKey。
   *
   * @param excludedNodeIds 需要排除的节点 ID 集合（已尝试失败的节点）
   * @param routingKey 路由键（任务 jobKey，可为 null）
   * @return 选中的 Worker 节点；无可用 Worker 时返回 null
   */
  public JobNodeVO selectWorker(Set<String> excludedNodeIds, String routingKey) {
    List<JobNodeVO> onlineNodes = getOnlineNodes();
    if (onlineNodes.isEmpty()) {
      log.debug("[WorkerSelector] 无在线节点");
      return null;
    }

    String localNodeId = resolveLocalNodeId();
    // 排除 Leader 节点和已尝试失败的节点
    List<JobNodeVO> workers =
        onlineNodes.stream()
            .filter(n -> !n.getNodeId().equals(localNodeId))
            .filter(n -> !excludedNodeIds.contains(n.getNodeId()))
            .toList();

    if (workers.isEmpty()) {
      log.debug("[WorkerSelector] 无可用 Worker 节点(仅 Leader 在线或全部已排除)");
      return null;
    }

    String strategy =
        cronjobProperties.getSchedulerExecutorSeparation().getWorkerSelectionStrategy();
    if ("least_load".equalsIgnoreCase(strategy)) {
      return selectLeastLoad(workers);
    }
    if ("random".equalsIgnoreCase(strategy)) {
      return selectRandom(workers);
    }
    if ("consistent_hash".equalsIgnoreCase(strategy)) {
      return selectConsistentHash(workers, routingKey);
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
  private JobNodeVO selectRoundRobin(List<JobNodeVO> workers) {
    int idx = Math.abs(roundRobinCounter.getAndIncrement()) % workers.size();
    return workers.get(idx);
  }

  /**
   * 随机选择 Worker 节点。
   *
   * @param workers 可用 Worker 列表
   * @return 选中的 Worker 节点
   */
  private JobNodeVO selectRandom(List<JobNodeVO> workers) {
    int idx = ThreadLocalRandom.current().nextInt(workers.size());
    return workers.get(idx);
  }

  /**
   * 按路由键一致哈希选择 Worker 节点。
   *
   * <p>同一 routingKey 在节点列表不变时恒定命中同一节点（对 Worker 上下线不敏感，近似一致性）。
   * 哈希算法与分片策略一致的 FNV-1a 32 位实现，保证同一 jobKey 的哈希值稳定。
   *
   * @param workers 可用 Worker 列表
   * @param routingKey 路由键（jobKey）
   * @return 选中的 Worker 节点；routingKey 为空时回退轮询
   */
  private JobNodeVO selectConsistentHash(List<JobNodeVO> workers, String routingKey) {
    if (routingKey == null || routingKey.isBlank()) {
      return selectRoundRobin(workers);
    }
    int hash = fnv1a32(routingKey);
    int idx = Math.floorMod(hash, workers.size());
    return workers.get(idx);
  }

  /**
   * FNV-1a 32 位哈希（与分片策略同源，避免引入第三方依赖）。
   *
   * @param input 输入字符串
   * @return 32 位无符号哈希值
   */
  private int fnv1a32(String input) {
    int hash = FNV1A_OFFSET_BASIS;
    byte[] bytes = input.getBytes(StandardCharsets.UTF_8);
    for (byte b : bytes) {
      hash ^= (b & BYTE_UNSIGNED_MASK);
      hash *= FNV1A_PRIME;
    }
    return hash;
  }

  /**
   * 最小负载选择 Worker 节点。
   *
   * <p>优先选择 runningCount 最小的节点；并列时选择 cpu_usage 最低的节点（基于心跳上报的
   * {@link JobNodeVO#getCpuUsage()}，null 视为 0 即最低优先）；仍并列时按 nodeId 升序（保证确定性）。
   *
   * @param workers 可用 Worker 列表
   * @return 选中的 Worker 节点
   */
  private JobNodeVO selectLeastLoad(List<JobNodeVO> workers) {
    return workers.stream()
        .min(
            (a, b) -> {
              int loadA = a.getRunningCount() != null ? a.getRunningCount() : 0;
              int loadB = b.getRunningCount() != null ? b.getRunningCount() : 0;
              int cmp = Integer.compare(loadA, loadB);
              if (cmp != 0) {
                return cmp;
              }
              BigDecimal cpuA =
                  a.getCpuUsage() != null ? a.getCpuUsage() : BigDecimal.ZERO;
              BigDecimal cpuB =
                  b.getCpuUsage() != null ? b.getCpuUsage() : BigDecimal.ZERO;
              cmp = cpuA.compareTo(cpuB);
              return cmp != 0 ? cmp : a.getNodeId().compareTo(b.getNodeId());
            })
        .orElse(workers.get(0));
  }

  /** 获取在线节点列表。 */
  private List<JobNodeVO> getOnlineNodes() {
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

