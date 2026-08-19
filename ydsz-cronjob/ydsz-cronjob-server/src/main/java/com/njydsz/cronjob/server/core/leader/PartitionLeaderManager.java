package com.njydsz.cronjob.server.core.leader;

import java.time.Duration;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

import com.njydsz.cronjob.infra.entity.job.Job;
import com.njydsz.cronjob.server.config.CronjobProperties;

/**
 * P2-9: 多 Active Leader 分区调度管理器。
 *
 * <p>将调度集群分为 N 个分区，每个分区有独立的 Leader 选举。 节点启动时尝试抢占所有分区的 Leader 角色，成功持有的分区由本节点负责扫描调度。
 *
 * <h3>分区分配策略</h3>
 *
 * <ul>
 *   <li>任务按 {@code hash(jobKey) % totalPartitions} 分配到分区
 *   <li>每个分区有独立的 Leader 选举（Redis 锁 key: {@code ydsz:job:leader:{role}-{partition}}）
 *   <li>单节点可同时持有多个分区的 Leader（节点数 &lt; 分区数时）
 *   <li>节点宕机后，其分区 Leader 释放，其他节点抢占
 * </ul>
 *
 * <h3>与 JobScanner 的协作</h3>
 *
 * <ul>
 *   <li>JobScanner 扫描到任务后，通过 {@link #isMyPartition(Job)} 判断是否属于本节点分区
 *   <li>不属于本节点分区的任务跳过，由对应分区 Leader 负责扫描派发
 * </ul>
 *
 * <p>对标 PowerJob 的多分区调度能力，提升调度吞吐量和可用性。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(
    prefix = "ydsz.cronjob.leader.partition",
    name = "enabled",
    havingValue = "true")
public class PartitionLeaderManager {

  private final LeaderElector leaderElector;
  private final CronjobProperties cronjobProperties;

  /** 当前节点持有的分区集合 */
  private final Set<Integer> heldPartitions = ConcurrentHashMap.newKeySet();

  /** 分区总数 */
  private int totalPartitions;

  /** 基础角色名 */
  private String baseRole;

  /**
   * 初始化分区调度：解析分区总数与基础角色名，并初始抢占所有分区 Leader。
   *
   * <p>节点启动时即尝试抢占 {@code [0, totalPartitions)} 全部分区的 Leader 角色 （见 {@link
   * #tryAcquireAllPartitions()}），使单节点在分区数较多时可同时持有多分区； 后续由 {@link #renewAndAcquirePartitions()}
   * 周期续期已持有分区并尝试抢占他人释放的分区。 仅当 {@code ydsz.cronjob.leader.partition.enabled=true} 时本 Bean 才注册。
   */
  @PostConstruct
  public void init() {
    this.totalPartitions = cronjobProperties.getLeader().getPartition().getTotalPartitions();
    this.baseRole = cronjobProperties.getLeader().getRole();
    log.info(
        "[PartitionLeader] 分区调度初始化: totalPartitions={} baseRole={}", totalPartitions, baseRole);
    // 初始抢占所有分区
    tryAcquireAllPartitions();
  }

  /**
   * 尝试抢占所有分区的 Leader 角色。
   *
   * <p>节点启动时调用，尝试抢占 0 ~ totalPartitions-1 的所有分区。 已持有的分区跳过，未持有的尝试抢占。
   */
  public void tryAcquireAllPartitions() {
    Duration lease = Duration.ofSeconds(cronjobProperties.getLeader().getLeaseSeconds());
    for (int i = 0; i < totalPartitions; i++) {
      if (heldPartitions.contains(i)) {
        continue;
      }
      String role = partitionRole(i);
      if (leaderElector.tryAcquire(role, lease)) {
        heldPartitions.add(i);
        log.info("[PartitionLeader] 抢占分区 Leader 成功: partition={} role={}", i, role);
      }
    }
    log.info("[PartitionLeader] 当前持有分区: {} / total={}", heldPartitions, totalPartitions);
  }

  /**
   * 判断任务是否属于当前节点持有的分区。
   *
   * <p>分区分配策略：
   *
   * <ul>
   *   <li>{@code job_key}（默认）: {@code Math.abs(jobKey.hashCode()) % totalPartitions}
   *   <li>{@code job_group}: {@code Math.abs(jobGroup.hashCode()) % totalPartitions}
   * </ul>
   *
   * @param job 任务定义
   * @return true 属于本节点分区（应扫描派发）；false 不属于（跳过）
   */
  public boolean isMyPartition(Job job) {
    int partition = computePartition(job);
    return heldPartitions.contains(partition);
  }

  /**
   * 计算任务所属分区。
   *
   * @param job 任务定义
   * @return 分区索引 [0, totalPartitions)
   */
  public int computePartition(Job job) {
    String hashStrategy = cronjobProperties.getLeader().getPartition().getHashStrategy();
    String hashKey;
    if ("job_group".equalsIgnoreCase(hashStrategy)) {
      hashKey = job.getJobGroup() != null ? job.getJobGroup() : job.getJobKey();
    } else {
      hashKey = job.getJobKey();
    }
    return Math.abs(hashKey.hashCode()) % totalPartitions;
  }

  /**
   * 获取当前节点持有的分区集合（不可变）。
   *
   * @return 持有的分区集合
   */
  public Set<Integer> getHeldPartitions() {
    return Collections.unmodifiableSet(heldPartitions);
  }

  /**
   * 定时续期所有持有的分区 Leader 租约，并尝试抢占未持有的分区。
   *
   * <p>默认每 10s 执行一次（与 Leader 续期间隔对齐）。
   */
  @Scheduled(fixedDelayString = "${ydsz.cronjob.leader.renew-interval-seconds:10}s")
  public void renewAndAcquirePartitions() {
    Duration lease = Duration.ofSeconds(cronjobProperties.getLeader().getLeaseSeconds());
    // 续期已持有的分区
    heldPartitions.removeIf(
        partition -> {
          String role = partitionRole(partition);
          if (!leaderElector.renew(role)) {
            log.warn("[PartitionLeader] 分区续期失败, 尝试重新抢占: partition={}", partition);
            // 尝试重新抢占
            if (leaderElector.tryAcquire(role, lease)) {
              log.info("[PartitionLeader] 重新抢占分区成功: partition={}", partition);
              return false; // 保持持有
            }
            return true; // 移除：抢占失败
          }
          return false; // 保持持有
        });
    // 尝试抢占未持有的分区（可能有其他节点释放）
    for (int i = 0; i < totalPartitions; i++) {
      if (!heldPartitions.contains(i)) {
        String role = partitionRole(i);
        if (leaderElector.tryAcquire(role, lease)) {
          heldPartitions.add(i);
          log.info("[PartitionLeader] 新抢占分区: partition={}", i);
        }
      }
    }
  }

  /** 优雅下线：释放所有持有的分区 Leader。 */
  @PreDestroy
  public void shutdown() {
    for (int partition : heldPartitions) {
      leaderElector.release(partitionRole(partition));
    }
    heldPartitions.clear();
    log.info("[PartitionLeader] 释放所有分区 Leader");
  }

  /**
   * 构造分区角色名。
   *
   * @param partition 分区索引
   * @return 角色名（如 {@code ydsz-job-scheduler-0}）
   */
  private String partitionRole(int partition) {
    return baseRole + "-" + partition;
  }
}
