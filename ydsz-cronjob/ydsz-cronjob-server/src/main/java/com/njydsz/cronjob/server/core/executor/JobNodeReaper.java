package com.njydsz.cronjob.server.core.executor;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import jakarta.annotation.PostConstruct;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;

import com.njydsz.cronjob.domain.entity.log.JobLog;
import com.njydsz.cronjob.infra.mapper.job.JobNodeMapper;
import com.njydsz.cronjob.infra.mapper.log.JobLogMapper;
import com.njydsz.cronjob.server.config.CronjobProperties;
import com.njydsz.cronjob.server.core.LockKeyUtil;
import com.njydsz.cronjob.server.core.leader.LeaderElector;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 僵尸节点回收器（P0-8） + 节点掉线故障转移（P1-3）。
 *
 * <p>定时清理 {@code ydsz_job_node} 表中的僵尸节点，并对掉线节点上的 RUNNING 任务执行故障转移：
 * <ol>
 *   <li>P1-3 故障转移：查询掉线节点上 RUNNING 状态的日志，释放 Redis 锁，标记为 FAILED</li>
 *   <li>将超时仍为 ONLINE 的节点标记为 OFFLINE（节点未优雅下线，如 kill -9 / 宕机）</li>
 *   <li>物理删除已离线超过 30 分钟的节点记录，避免表无限膨胀</li>
 * </ol>
 *
 * <h3>执行条件</h3>
 * <ul>
 *   <li>仅当 {@code ydsz.cronjob.leader.enabled=true} 时启用</li>
 *   <li>仅 Leader 节点执行，避免多实例重复清理</li>
 *   <li>P1-1: 仅在 {@code ydsz.cronjob.node-discovery.type=db} 时注册
 *       （type=nacos 时由 Nacos 自动管理节点上下线，无需回收）</li>
 * </ul>
 *
 * <p>清理阈值：
 * <ul>
 *   <li>僵尸判定：{@code last_heartbeat < NOW() - offlineThresholdSeconds}（默认 30s）</li>
 *   <li>记录删除：{@code last_heartbeat < NOW() - 30min}（硬编码，避免误删刚下线节点）</li>
 * </ul>
 *
 * <h3>P1-3 故障转移策略</h3>
 * <p>对标 XXL-Job / PowerJob：节点掉线时，其上 RUNNING 任务不会自动迁移到其他节点立即重跑，
 * 而是标记为 FAILED 并释放分布式锁，等待任务的下一次 Cron 触发自然重新执行。
 * 释放锁的目的是避免下次触发时因锁未过期而被误判为「仍在执行」导致跳过。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnBean(LeaderElector.class)
@ConditionalOnProperty(name = "ydsz.cronjob.node-discovery.type", havingValue = "db")
public class JobNodeReaper {

    private final JobNodeMapper jobNodeMapper;
    private final JobLogMapper jobLogMapper;
    private final LeaderElector leaderElector;
    private final CronjobProperties cronjobProperties;
    private final RedisTemplate<String, Object> redisTemplate;

    /** 离线节点记录保留时长（分钟），超过此时长才物理删除 */
    private static final long STALE_NODE_RETENTION_MINUTES = 30;

    /** Lua 脚本: 安全释放锁（仅当 value 匹配时才 delete，避免误删其他节点持有的锁） */
    private static final DefaultRedisScript<Long> RELEASE_LOCK_SCRIPT;
    static {
        RELEASE_LOCK_SCRIPT = new DefaultRedisScript<>();
        RELEASE_LOCK_SCRIPT.setScriptText(LockKeyUtil.RELEASE_LOCK_SCRIPT);
        RELEASE_LOCK_SCRIPT.setResultType(Long.class);
    }

    private String leaderRole;

    /**
     * 初始化僵尸节点回收器：解析 Leader 角色。
     *
     * <p>本类由 {@code @ConditionalOnBean(LeaderElector.class)} 与
     * {@code @ConditionalOnProperty(node-discovery.type=db)} 限定仅 DB 节点发现模式下注册。
     * 初始化仅缓存 Leader 角色用于日志与 Leader 身份校验；
     * 回收阈值 {@code STALE_NODE_RETENTION_MINUTES}（30min）固定，无外部资源需要预分配。
     */
    @PostConstruct
    public void init() {
        this.leaderRole = cronjobProperties.getLeader().getRole();
        log.info("[JobNodeReaper] 初始化完成, role={} retentionMinutes={}",
                leaderRole, STALE_NODE_RETENTION_MINUTES);
    }

    /**
     * 定时清理僵尸节点（默认每 5 分钟一次）。
     */
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
     * <p>P1-3: 在标记 OFFLINE 之前，先对这些节点上的 RUNNING 任务执行故障转移
     * （释放 Redis 锁 + 标记日志为 FAILED），避免任务永久卡在 RUNNING 状态。
     */
    private void markStaleNodes() {
        long offlineThreshold = cronjobProperties.getExecutor().getOfflineThresholdSeconds();
        LocalDateTime cutoff = LocalDateTime.now().minusSeconds(offlineThreshold);

        // P1-3: 先查询即将被标记为 OFFLINE 的节点，执行故障转移
        List<String> staleNodeIds = jobNodeMapper.selectStaleOnlineNodeIds(cutoff);
        if (!staleNodeIds.isEmpty()) {
            for (String nodeId : staleNodeIds) {
                failoverNode(nodeId);
            }
        }

        // 标记为 OFFLINE
        int affected = jobNodeMapper.markStaleOnlineAsOffline(cutoff);
        if (affected > 0) {
            log.warn("[JobNodeReaper] 标记 {} 个僵尸节点为 OFFLINE (heartbeat < {})",
                    affected, cutoff);
        }
    }

    /**
     * P1-3: 节点掉线故障转移。
     *
     * <p>对掉线节点上 RUNNING 状态的任务执行：
     * <ol>
     *   <li>释放 Redis 分布式锁（Lua 脚本安全释放，使用 logHolder 匹配）</li>
     *   <li>标记日志为 FAILED（error_message='Node went offline during execution'）</li>
     * </ol>
     *
     * <p>注意：不主动推进 next_fire_time，让正常 Cron 调度在下次触发时重新执行。
     * 释放锁是为了确保下次触发时能成功获取锁（避免 TTL 内被误判为仍在执行）。
     */
    private void failoverNode(String nodeId) {
        try {
            List<JobLog> runningLogs = jobLogMapper.selectRunningByNode(nodeId);
            if (runningLogs.isEmpty()) {
                return;
            }
            log.warn("[JobNodeReaper] 故障转移开始: nodeId={} runningTasks={}",
                    nodeId, runningLogs.size());

            // 1. 释放 Redis 锁（best-effort，P1-4: 支持分片和非分片任务）
            for (JobLog logEntry : runningLogs) {
                releaseLockSafe(logEntry);
            }

            // 2. 标记日志为 FAILED（批量）
            int marked = jobLogMapper.markFailedByNodeOffline(nodeId, LocalDateTime.now());
            log.warn("[JobNodeReaper] 故障转移完成: nodeId={} markedFailed={} releasedLocksAttempt={}",
                    nodeId, marked, runningLogs.size());
        } catch (Exception e) {
            log.error("[JobNodeReaper] 故障转移异常: nodeId={} reason={}",
                    nodeId, e.getMessage(), e);
        }
    }

    /**
     * 安全释放锁（Lua 脚本，仅当 lockHolder 匹配时才 delete）。
     *
     * <p>P1-4: 支持分片任务锁释放。根据日志的 shardIndex 字段重建完整 lockKey：
     * <ul>
     *   <li>非分片任务（shardIndex=null）：{@code ydsz:job:lock:{jobKey}}</li>
     *   <li>分片任务（shardIndex>=0）：{@code ydsz:job:lock:{jobKey}:shard:{shardIndex}}</li>
     * </ul>
     * Lua 脚本在 key 不存在或 value 不匹配时返回 0，无副作用。
     *
     * @param logEntry 任务日志（含 jobKey、lockHolder、shardIndex）
     */
    private void releaseLockSafe(JobLog logEntry) {
        String lockHolder = logEntry.getLockHolder();
        if (lockHolder == null || lockHolder.isBlank()) {
            return;
        }
        // P0-11: 统一通过 LockKeyUtil 构造 lockKey（含分片感知）
        String lockKey = LockKeyUtil.buildJobLockKey(logEntry.getJobKey(), logEntry.getShardIndex());
        try {
            Long result = redisTemplate.execute(RELEASE_LOCK_SCRIPT,
                    Collections.singletonList(lockKey), lockHolder);
            if (result != null && result > 0) {
                log.info("[JobNodeReaper] 释放锁成功: key={} holder={}", lockKey, lockHolder);
            }
        } catch (Exception e) {
            log.warn("[JobNodeReaper] 释放锁失败(将等待 TTL 自动过期): key={} reason={}",
                    lockKey, e.getMessage());
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
