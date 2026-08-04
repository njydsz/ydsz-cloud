package com.remisoft.cronjob.server.core.scheduler;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

import com.remisoft.cronjob.domain.entity.job.Job;
import com.remisoft.cronjob.infra.mapper.job.JobMapper;
import com.remisoft.cronjob.server.config.CronjobProperties;
import com.remisoft.cronjob.server.core.dispatch.DefaultTaskDispatcher;
import com.remisoft.cronjob.server.core.dispatch.TaskDispatcher;
import com.remisoft.cronjob.server.core.leader.LeaderElector;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 秒级调度器（P0-3）。
 *
 * <p>用于调度 {@link ScheduleType#FIXED_RATE} 和 {@link ScheduleType#FIXED_DELAY} 类型的任务，
 * 弥补 {@code JobScanner}（仅扫描 CRON 类型）与 Spring {@code CronTrigger}（仅支持 Cron 表达式）
 * 无法覆盖固定频率/固定延迟调度场景的不足。对标 PowerJob 的 FixedRate / FixedDelay 调度方式。
 *
 * <h3>启用条件</h3>
 * <ul>
 *   <li>Leader 模式启用（{@code @ConditionalOnBean(LeaderElector.class)}）</li>
 *   <li>仅 Leader 节点实际执行任务派发（Follower 节点注册但不派发，避免重复执行）</li>
 * </ul>
 *
 * <h3>调度语义</h3>
 * <ul>
 *   <li>{@link ScheduleType#FIXED_RATE}: {@code scheduleAtFixedRate(task, 0, fixedRateMs, MILLISECONDS)}
 *       每 N 毫秒执行一次，不等上次完成（可能重叠，由分布式锁兜底互斥）</li>
 *   <li>{@link ScheduleType#FIXED_DELAY}: {@code scheduleWithFixedDelay(task, 0, fixedDelayMs, MILLISECONDS)}
 *       上次执行完成后等待 N 毫秒再执行下一次</li>
 * </ul>
 *
 * <p>任务执行时通过 {@link TaskDispatcher#dispatch} 派发，triggerType={@link DefaultTaskDispatcher#TRIGGER_CRON}，
 * 复用现有的分布式锁、日志、重试、告警等基础设施。
 *
 * @author remi-team
 * @since 1.0.0
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnBean(LeaderElector.class)
public class SecondLevelScheduler {

    /** 任务定义 Mapper */
    private final JobMapper jobMapper;
    /** 任务派发器（Leader 模式下由 DefaultTaskDispatcher 提供） */
    private final TaskDispatcher taskDispatcher;
    /** Leader 选举器（用于判断当前节点是否为 Leader） */
    private final LeaderElector leaderElector;
    /** 调度配置属性 */
    private final CronjobProperties cronjobProperties;

    /** 调度线程池（核心线程数 = schedulerPoolSize） */
    private ScheduledExecutorService scheduler;

    /** 已注册的调度任务: jobId -> ScheduledFuture */
    private final Map<String, ScheduledFuture<?>> scheduledMap = new ConcurrentHashMap<>();

    /** Leader 角色（从配置读取，便于多套调度集群隔离） */
    private String leaderRole;

    /**
     * 初始化调度器并加载所有 FIXED_RATE/FIXED_DELAY 类型的 NORMAL 任务。
     */
    @PostConstruct
    public void init() {
        this.leaderRole = cronjobProperties.getLeader().getRole();
        int poolSize = Math.max(2, cronjobProperties.getSchedulerPoolSize());
        this.scheduler = Executors.newScheduledThreadPool(poolSize, buildThreadFactory());
        log.info("[SecondLevelScheduler] 初始化完成, poolSize={}, role={}", poolSize, leaderRole);
        try {
            reload();
        } catch (Exception e) {
            log.error("[SecondLevelScheduler] 启动加载任务失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 优雅关闭线程池。
     */
    @PreDestroy
    public void shutdown() {
        log.info("[SecondLevelScheduler] 关闭中, 待取消任务数={}", scheduledMap.size());
        scheduledMap.values().forEach(f -> {
            try {
                f.cancel(false);
            } catch (Exception ignored) {
                // 忽略取消异常
            }
        });
        scheduledMap.clear();
        if (scheduler != null) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(10, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                scheduler.shutdownNow();
            }
        }
        log.info("[SecondLevelScheduler] 已关闭");
    }

    /**
     * 重新加载所有 FIXED_RATE/FIXED_DELAY 类型的 NORMAL 任务。
     *
     * <p>先清空已注册任务，再全量重新加载。供启动时和外部手动 reload 调用。
     */
    public void reload() {
        // 先清空所有已注册任务（避免旧任务残留）
        scheduledMap.values().forEach(f -> {
            try {
                f.cancel(false);
            } catch (Exception ignored) {
                // 忽略取消异常
            }
        });
        scheduledMap.clear();
        List<Job> allNormal = jobMapper.selectAllNormal();
        int count = 0;
        for (Job job : allNormal) {
            ScheduleType type = ScheduleType.parse(job.getScheduleType());
            if (type == ScheduleType.FIXED_RATE || type == ScheduleType.FIXED_DELAY) {
                try {
                    long intervalMs;
                    if (type == ScheduleType.FIXED_RATE) {
                        intervalMs = validateInterval(job.getFixedRateMs(), job.getJobKey(), "fixedRateMs");
                    } else {
                        intervalMs = validateInterval(job.getFixedDelayMs(), job.getJobKey(), "fixedDelayMs");
                    }
                    if (intervalMs <= 0) {
                        log.warn("[SecondLevelScheduler] reload 跳过非法间隔: key={} type={}",
                                job.getJobKey(), type);
                        continue;
                    }
                    registerInternal(job, type, intervalMs);
                    count++;
                } catch (Exception e) {
                    log.warn("[SecondLevelScheduler] 注册任务失败: key={} reason={}",
                            job.getJobKey(), e.getMessage());
                }
            }
        }
        log.info("[SecondLevelScheduler] 重新加载完成, 已注册任务数={}/{}",
                count, allNormal.size());
    }

    /**
     * 注册任务到调度器（动态新增/更新时调用）。
     *
     * <p>仅 FIXED_RATE / FIXED_DELAY 类型任务会被注册；
     * CRON / API 类型直接返回 false（由 JobScanner 或手动触发处理）。
     *
     * @param job 任务定义
     * @return 注册成功返回 true；非 FIXED_RATE/FIXED_DELAY 类型或参数非法返回 false
     */
    public boolean register(Job job) {
        if (job == null || job.getId() == null) {
            return false;
        }
        if (!"NORMAL".equals(job.getStatus())) {
            return false;
        }
        ScheduleType type = ScheduleType.parse(job.getScheduleType());
        if (type != ScheduleType.FIXED_RATE && type != ScheduleType.FIXED_DELAY) {
            // CRON / API 类型不由此调度器管理
            return false;
        }
        // 校验间隔参数（在调用 registerInternal 之前检查，避免无效注册）
        long intervalMs;
        if (type == ScheduleType.FIXED_RATE) {
            intervalMs = validateInterval(job.getFixedRateMs(), job.getJobKey(), "fixedRateMs");
        } else {
            intervalMs = validateInterval(job.getFixedDelayMs(), job.getJobKey(), "fixedDelayMs");
        }
        if (intervalMs <= 0) {
            return false;
        }
        try {
            registerInternal(job, type, intervalMs);
            return true;
        } catch (Exception e) {
            log.error("[SecondLevelScheduler] 注册任务失败: key={} reason={}",
                    job.getJobKey(), e.getMessage());
            return false;
        }
    }

    /**
     * 注销任务（删除/暂停/更新时调用）。
     *
     * @param jobId 任务 ID
     * @return 注销成功返回 true；任务未注册返回 false
     */
    public boolean unregister(String jobId) {
        if (jobId == null) {
            return false;
        }
        ScheduledFuture<?> f = scheduledMap.remove(jobId);
        if (f != null) {
            f.cancel(false);
            log.info("[SecondLevelScheduler] 注销任务: jobId={}", jobId);
            return true;
        }
        return false;
    }

    /**
     * 内部注册逻辑：先注销已有调度，再按调度类型注册到 ScheduledExecutorService。
     *
     * @param job        任务定义
     * @param type       调度类型（FIXED_RATE / FIXED_DELAY）
     * @param intervalMs 调度间隔（毫秒，已校验 > 0）
     */
    private void registerInternal(Job job, ScheduleType type, long intervalMs) {
        // 先注销已有调度（避免重复注册）
        unregister(job.getId());
        Runnable task = buildTask(job);
        ScheduledFuture<?> future;
        if (type == ScheduleType.FIXED_RATE) {
            // 固定频率：每 N 毫秒执行一次（不等上次完成，可能重叠，由分布式锁兜底）
            future = scheduler.scheduleAtFixedRate(task, 0L, intervalMs, TimeUnit.MILLISECONDS);
        } else {
            // 固定延迟：上次完成后等 N 毫秒再执行
            future = scheduler.scheduleWithFixedDelay(task, 0L, intervalMs, TimeUnit.MILLISECONDS);
        }
        scheduledMap.put(job.getId(), future);
        log.info("[SecondLevelScheduler] 注册任务成功: key={} type={} intervalMs={}",
                job.getJobKey(), type, intervalMs);
    }

    /**
     * 校验固定间隔参数（必须 > 0）。
     *
     * @param interval 间隔毫秒数
     * @param jobKey   任务 KEY（日志用）
     * @param fieldName 字段名（日志用）
     * @return 合法的间隔毫秒数；非法返回 -1
     */
    private long validateInterval(Long interval, String jobKey, String fieldName) {
        if (interval == null || interval <= 0) {
            log.warn("[SecondLevelScheduler] {} 非法: key={} value={}", fieldName, jobKey, interval);
            return -1L;
        }
        return interval;
    }

    /**
     * 构造任务执行体。
     *
     * <p>执行前检查 Leader 身份：非 Leader 节点跳过派发（避免重复执行）。
     * 派发使用 {@link DefaultTaskDispatcher#TRIGGER_CRON} 触发类型，
     * 复用现有的分布式锁、日志、重试、告警等基础设施。
     *
     * @param job 任务定义
     * @return Runnable 任务执行体
     */
    private Runnable buildTask(Job job) {
        return () -> {
            try {
                // 仅 Leader 节点派发任务（Follower 跳过，避免重复执行）
                if (!leaderElector.isLeader(leaderRole)) {
                    log.debug("[SecondLevelScheduler] 非 Leader 节点, 跳过派发: key={}", job.getJobKey());
                    return;
                }
                // triggerType=CRON：派发器内部会抢锁、写日志、重试、告警
                taskDispatcher.dispatch(job, null, DefaultTaskDispatcher.TRIGGER_CRON);
            } catch (Exception e) {
                log.error("[SecondLevelScheduler] 任务派发异常: key={} reason={}",
                        job.getJobKey(), e.getMessage(), e);
            }
        };
    }

    /**
     * 构造调度线程池的线程工厂（守护线程，命名前缀 remi-job-fixed-）。
     *
     * @return ThreadFactory 实例
     */
    private ThreadFactory buildThreadFactory() {
        return r -> {
            Thread t = new Thread(r, "remi-job-fixed-" + System.nanoTime());
            t.setDaemon(true);
            return t;
        };
    }

    /**
     * 暴露已注册任务数（仅供测试断言使用）。
     */
    int getRegisteredCount() {
        return scheduledMap.size();
    }

    /**
     * 判断任务是否已注册（仅供测试断言使用）。
     */
    boolean isRegistered(String jobId) {
        return scheduledMap.containsKey(jobId);
    }

    /**
     * 获取 Leader 角色（仅供测试断言使用）。
     */
    String getLeaderRole() {
        return StringUtils.hasText(leaderRole) ? leaderRole : "";
    }
}
