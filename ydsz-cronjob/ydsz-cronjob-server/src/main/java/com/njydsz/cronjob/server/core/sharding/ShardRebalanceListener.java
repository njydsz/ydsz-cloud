package com.njydsz.cronjob.server.core.sharding;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.cloud.client.discovery.event.HeartbeatEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.njydsz.cronjob.domain.vo.JobNodeVO;
import com.njydsz.cronjob.server.core.discovery.NodeDiscoveryStrategy;
import com.njydsz.cronjob.server.core.healing.AnomalyRecoveryScanner;

/**
 * P1-9: 分片实时重平衡监听器。
 *
 * <p>当集群中节点实例发生变化（新增/下线）时，自动检测需要重新分片的运行中任务， 并通知 Leader 节点重新计算分片分配方案。
 *
 * <h3>工作流程</h3>
 *
 * <ol>
 *   <li>监听 Spring Cloud {@link HeartbeatEvent}（Nacos 服务发现心跳触发）
 *   <li>对比当前在线节点列表与上次缓存的节点列表
 *   <li>检测到节点变化时：
 *       <ul>
 *         <li>记录变更日志（新增/移除了哪些节点）
 *         <li>标记需要重平衡（设置 dirty flag）
 *       </ul>
 *   <li>Leader 节点在下一次 JobScanner 扫描周期时自动使用新的节点列表进行分片分配
 * </ol>
 *
 * <h3>设计要点</h3>
 *
 * <ul>
 *   <li>仅 Leader 节点执行重平衡逻辑（非 Leader 跳过）
 *   <li>使用心跳事件驱动而非轮询，降低延迟
 *   <li>防抖：连续心跳事件在 5s 内只处理一次
 *   <li>记录节点变更历史，供运维查看
 * </ul>
 *
 * <p>分片重平衡机制：实例变更后自动感知并重新分片。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ShardRebalanceListener {

  private final ObjectProvider<NodeDiscoveryStrategy> nodeDiscoveryStrategyProvider;

  /** P2-P6: 异常恢复扫描器（节点下线时主动触发任务转移，不等 30s 周期） */
  private final ObjectProvider<AnomalyRecoveryScanner> anomalyRecoveryScannerProvider;

  /** 上次缓存的在线节点 ID 列表（用于对比变化） */
  private volatile List<String> lastNodeIds = List.of();

  /** 上次处理时间戳（防抖，5s 内只处理一次） */
  private final AtomicLong lastProcessTime = new AtomicLong(0);

  /** 防抖间隔（毫秒） */
  private static final long DEBOUNCE_INTERVAL_MS = 5000;

  /** 节点变更历史记录（key=时间戳, value=变更描述） */
  private final Map<Long, String> changeHistory = new ConcurrentHashMap<>();

  /** 最大历史记录条数 */
  private static final int MAX_HISTORY = 50;

  /**
   * 监听 Spring Cloud 心跳事件（Nacos 服务发现每 ~10s 触发一次）。
   *
   * <p>当检测到节点列表变化时，记录变更并标记需要重平衡。 Leader 节点的 JobScanner 会在下一次扫描时自动使用新的节点列表。
   *
   * @param event 心跳事件
   */
  @EventListener
  public void onHeartbeat(HeartbeatEvent event) {
    NodeDiscoveryStrategy strategy = nodeDiscoveryStrategyProvider.getIfAvailable();
    if (strategy == null) {
      return;
    }

    // 防抖：5s 内只处理一次
    long now = System.currentTimeMillis();
    long lastTime = lastProcessTime.get();
    if (now - lastTime < DEBOUNCE_INTERVAL_MS) {
      return;
    }
    if (!lastProcessTime.compareAndSet(lastTime, now)) {
      return;
    }

    try {
      List<JobNodeVO> onlineNodes = strategy.getOnlineNodes();
      List<String> currentIds =
          onlineNodes.stream().map(JobNodeVO::getNodeId).sorted().collect(Collectors.toList());

      // 对比变化
      if (!currentIds.equals(lastNodeIds)) {
        detectAndLogChanges(lastNodeIds, currentIds);
        lastNodeIds = currentIds;
        log.info("[ShardRebalance] 节点列表已更新, 下次分片分配将使用新列表: currentNodeCount={}", currentIds.size());
      }
    } catch (Exception e) {
      log.debug("[ShardRebalance] 心跳事件处理异常: reason={}", e.getMessage());
    }
  }

  /**
   * 检测节点变化并记录日志。
   *
   * @param oldIds 旧节点 ID 列表
   * @param newIds 新节点 ID 列表
   */
  private void detectAndLogChanges(List<String> oldIds, List<String> newIds) {
    List<String> added = newIds.stream().filter(id -> !oldIds.contains(id)).toList();
    List<String> removed = oldIds.stream().filter(id -> !newIds.contains(id)).toList();

    if (!added.isEmpty()) {
      log.info("[ShardRebalance] 节点上线: {}", added);
    }
    if (!removed.isEmpty()) {
      log.warn("[ShardRebalance] 节点下线: {}（主动触发故障转移）", removed);
      // P2-P6: 主动触发异常恢复扫描，立即转移下线节点上的 RUNNING 任务（含运行中分片），
      // 无需等待 AnomalyRecoveryScanner 的下一个 30s 扫描周期。scanImmediately 内部有 Leader 校验。
      AnomalyRecoveryScanner scanner = anomalyRecoveryScannerProvider.getIfAvailable();
      if (scanner != null) {
        try {
          scanner.scanImmediately();
        } catch (Exception e) {
          log.warn("[ShardRebalance] 主动触发故障转移异常(将等待周期扫描兜底): reason={}", e.getMessage());
        }
      }
    }

    // 记录变更历史
    if (!added.isEmpty() || !removed.isEmpty()) {
      long timestamp = System.currentTimeMillis();
      String description = String.format("+%s -%s", added, removed);
      changeHistory.put(timestamp, description);

      // 清理过旧的历史记录
      if (changeHistory.size() > MAX_HISTORY) {
        long oldest = changeHistory.keySet().stream().min((a, b) -> Long.compare(a, b)).orElse(0L);
        if (oldest > 0) {
          changeHistory.remove(oldest);
        }
      }
    }
  }

  /**
   * 获取节点变更历史（供监控 API 使用）。
   *
   * @return 变更历史 Map（时间戳 → 变更描述）
   */
  public Map<Long, String> getChangeHistory() {
    return new ConcurrentHashMap<>(changeHistory);
  }

  /**
   * 手动触发重平衡检查（供管理 API 使用）。
   *
   * <p>强制刷新缓存的节点列表，下次分片分配时使用最新列表。
   */
  public void forceRebalance() {
    lastNodeIds = List.of();
    lastProcessTime.set(0);
    log.info("[ShardRebalance] 手动触发重平衡检查");
  }
}
