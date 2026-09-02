package com.njydsz.cronjob.server.core.executor;

import java.time.LocalDateTime;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

import com.njydsz.cronjob.domain.repository.JobNodeRepository;
import com.njydsz.cronjob.server.config.CronjobProperties;
import com.njydsz.cronjob.server.core.leader.LeaderElector;

/**
 * 僵尸节点回收器（P0-8）。
 *
 * <p>定时清理 {@code ydsz_job_node} 表中的僵尸节点：
 *
 * <ol>
 *   <li>将超时仍为 ONLINE 的节点标记为 OFFLINE
 *   <li>物理删除已离线超过 30 分钟的节点记录，避免表无限膨胀
 * </ol>
 *
 * <p>P1-A3 职责收敛：下线节点上的 RUNNING 任务故障转移（释放锁 → 标记 FAILED → 立即重新派发）
 * 统一由 {@link AnomalyRecoveryScanner} 负责（30s 周期，全节点发现模式生效）。
 * 本类不再重复执行"释放锁 + 标记 FAILED"逻辑，避免同一场景两种处理策略（原 Reaper 仅标记不重派、
 * AnomalyRecovery 立即重派）并存导致的语义混乱。
 *
 * <h3>执行条件</h3>
 *
 * <ul>
 *   <li>仅当 {@code ydsz.cronjob.leader.enabled=true} 时启用
 *   <li>仅 Leader 节点执行，避免多实例重复清理
 *   <li>仅在 {@code ydsz.cronjob.node-discovery.type=db} 时注册 （type=nacos 时由 Nacos
 *       自动管理节点上下线，无需回收）
 * </ul>
 *
 * <p>清理阈值：
 *
 * <ul>
 *   <li>僵尸判定：{@code last_heartbeat < NOW() - offlineThresholdSeconds}（默认 30s）
 *   <li>记录删除：{@code last_heartbeat < NOW() - 30min}（硬编码，避免误删刚下线节点）
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnBean(LeaderElector.class)
@ConditionalOnProperty(name = "ydsz.cronjob.node-discovery.type", havingValue = "db")
public class JobNodeReaper {

  private final JobNodeRepository jobNodeRepository;
  private final LeaderElector leaderElector;
  private final CronjobProperties cronjobProperties;

  /** 离线节点记录保留时长（分钟），超过此时长才物理删除 */
  private static final long STALE_NODE_RETENTION_MINUTES = 30;

  private String leaderRole;

  /**
   * 初始化僵尸节点回收器：解析 Leader 角色。
   *
   * <p>本类由 {@code @ConditionalOnBean(LeaderElector.class)} 与
   * {@code @ConditionalOnProperty(node-discovery.type=db)} 限定仅 DB 节点发现模式下注册。 初始化仅缓存 Leader 角色用于日志与
   * Leader 身份校验； 回收阈值 {@code STALE_NODE_RETENTION_MINUTES}（30min）固定，无外部资源需要预分配。
   */
  @PostConstruct
  public void init() {
    this.leaderRole = cronjobProperties.getLeader().getRole();
    log.info(
        "[JobNodeReaper] 初始化完成, role={} retentionMinutes={}",
        leaderRole,
        STALE_NODE_RETENTION_MINUTES);
  }

  /** 定时清理僵尸节点（默认每 5 分钟一次）。 */
  @Scheduled(fixedDelayString = "${ydsz.cronjob.node-reaper.interval-ms:300000}")
  public void reap() {
    if (!leaderElector.isLeader(leaderRole)) {
      return;
    }
    try {
      markStaleNodes();
      deleteStaleRecords();
    } catch (Exception e) {
      log.error("[JobNodeReaper] 清理僵尸节点异常: role={} reason={}", leaderRole, e.getMessage(), e);
    }
  }

  /**
   * 将超时仍为 ONLINE 的节点标记为 OFFLINE。
   *
   * <p>P1-A3: 下线节点上的 RUNNING 任务故障转移由 {@code AnomalyRecoveryScanner} 统一负责
   * （30s 周期，全节点发现模式生效，释放锁 + 标记 FAILED + 立即重新派发），本类不再重复处理，
   * 仅维护节点状态与清理记录。
   */
  private void markStaleNodes() {
    long offlineThreshold = cronjobProperties.getExecutor().getOfflineThresholdSeconds();
    LocalDateTime cutoff = LocalDateTime.now().minusSeconds(offlineThreshold);

    // 标记为 OFFLINE
    int affected = jobNodeRepository.markStaleOnlineAsOffline(cutoff);
    if (affected > 0) {
      log.warn("[JobNodeReaper] 标记 {} 个僵尸节点为 OFFLINE (heartbeat < {})", affected, cutoff);
    }
  }

  /** 物理删除已离线超过 30 分钟的节点记录。 */
  private void deleteStaleRecords() {
    LocalDateTime cutoff = LocalDateTime.now().minusMinutes(STALE_NODE_RETENTION_MINUTES);
    int affected = jobNodeRepository.deleteStaleOfflineNodes(cutoff);
    if (affected > 0) {
      log.info("[JobNodeReaper] 清理 {} 个过期离线节点记录 (heartbeat < {})", affected, cutoff);
    }
  }
}
