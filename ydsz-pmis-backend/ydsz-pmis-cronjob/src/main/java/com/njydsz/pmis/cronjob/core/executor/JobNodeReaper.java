package com.njydsz.pmis.cronjob.core.executor;

import com.njydsz.pmis.cronjob.config.CronjobProperties;
import com.njydsz.pmis.cronjob.core.leader.LeaderElector;
import com.njydsz.pmis.cronjob.mapper.JobNodeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.LocalDateTime;

/**
 * 僵尸节点回收器（P0-8）。
 *
 * <p>定时清理 {@code pmis_job_node} 表中的僵尸节点：
 * <ol>
 *   <li>将超时仍为 ONLINE 的节点标记为 OFFLINE（节点未优雅下线，如 kill -9 / 宕机）</li>
 *   <li>物理删除已离线超过 30 分钟的节点记录，避免表无限膨胀</li>
 * </ol>
 *
 * <h3>执行条件</h3>
 * <ul>
 *   <li>仅当 {@code pmis.cronjob.leader.enabled=true} 时启用</li>
 *   <li>仅 Leader 节点执行，避免多实例重复清理</li>
 * </ul>
 *
 * <p>清理阈值：
 * <ul>
 *   <li>僵尸判定：{@code last_heartbeat < NOW() - offlineThresholdSeconds}（默认 30s）</li>
 *   <li>记录删除：{@code last_heartbeat < NOW() - 30min}（硬编码，避免误删刚下线节点）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnBean(LeaderElector.class)
public class JobNodeReaper {

    private final JobNodeMapper jobNodeMapper;
    private final LeaderElector leaderElector;
    private final CronjobProperties cronjobProperties;

    /** 离线节点记录保留时长（分钟），超过此时长才物理删除 */
    private static final long STALE_NODE_RETENTION_MINUTES = 30;

    private String leaderRole;

    @jakarta.annotation.PostConstruct
    public void init() {
        this.leaderRole = cronjobProperties.getLeader().getRole();
        log.info("[JobNodeReaper] 初始化完成, role={} retentionMinutes={}",
                leaderRole, STALE_NODE_RETENTION_MINUTES);
    }

    /**
     * 定时清理僵尸节点（默认每 5 分钟一次）。
     */
    @Scheduled(fixedDelayString = "${pmis.cronjob.node-reaper.interval-ms:300000}")
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
     */
    private void markStaleNodes() {
        long offlineThreshold = cronjobProperties.getExecutor().getOfflineThresholdSeconds();
        LocalDateTime cutoff = LocalDateTime.now().minusSeconds(offlineThreshold);
        int affected = jobNodeMapper.markStaleOnlineAsOffline(cutoff);
        if (affected > 0) {
            log.warn("[JobNodeReaper] 标记 {} 个僵尸节点为 OFFLINE (heartbeat < {})",
                    affected, cutoff);
        }
    }

    /**
     * 物理删除已离线超过 30 分钟的节点记录。
     */
    private void deleteStaleRecords() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(STALE_NODE_RETENTION_MINUTES);
        int affected = jobNodeMapper.deleteStaleOfflineNodes(cutoff);
        if (affected > 0) {
            log.info("[JobNodeReaper] 清理 {} 个过期离线节点记录 (heartbeat < {})",
                    affected, cutoff);
        }
    }
}
