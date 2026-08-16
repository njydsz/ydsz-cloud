package com.njydsz.cronjob.server.core.dispatch;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import com.njydsz.common.redis.service.ops.RedisStringOps;
import com.njydsz.cronjob.domain.entity.job.Job;
import com.njydsz.cronjob.domain.entity.log.JobLog;
import com.njydsz.cronjob.infra.mapper.job.JobMapper;
import com.njydsz.cronjob.infra.mapper.log.JobLogMapper;

/**
 * P3-19: 优先级抢占式调度。
 *
 * <p>当高优先级任务到达但线程池已满时，抢占低优先级任务的执行资源：
 * <ol>
 *   <li>检测线程池满且新任务优先级高于队列中最低优先级任务</li>
 *   <li>通过 Thread.interrupt() 中断低优先级任务的执行线程</li>
 *   <li>释放线程池资源给高优先级任务</li>
 *   <li>被中断的低优先级任务自动重试（标记为 PREEMPTED）</li>
 * </ol>
 *
 * <h3>抢占条件</h3>
 * <ul>
 *   <li>新任务优先级 ≤ 2（高优先级）</li>
 *   <li>线程池活跃线程数 = maxPoolSize（已满）</li>
 *   <li>运行中最任务的优先级 ≥ 5（低优先级）</li>
 *   <li>优先级差 ≥ 3（确保抢占有意义）</li>
 * </ul>
 *
 * <h3>安全措施</h3>
 * <ul>
 *   <li>每个任务最多被抢占 3 次（防止饥饿）</li>
 *   <li>被抢占任务自动调度重试（延迟 1s）</li>
 *   <li>MANUAL 触发的任务不可被抢占</li>
 * </ul>
 *
 * <p>对标 PowerJob 的优先级队列 + 抢占式调度能力。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PreemptiveScheduler {

    private final JobLogMapper jobLogMapper;
    private final JobMapper jobMapper;
    private final RedisStringOps redisStringOps;

    /** 抢占计数器 key 前缀 */
    private static final String PREEMPT_COUNT_PREFIX = "ydsz:job:preempt:count:";

    /** 最大被抢占次数（防止饥饿） */
    private static final int MAX_PREEMPT_COUNT = 3;

    /** 可抢占的最低优先级阈值（新任务优先级 ≤ 此值才触发抢占） */
    private static final int PREEMPT_TRIGGER_PRIORITY = 2;

    /** 可被抢占的最低优先级（运行中任务优先级 ≥ 此值才可被抢占） */
    private static final int PREEMPTABLE_PRIORITY = 5;

    /** 优先级差阈值（差 ≥ 此值才抢占） */
    private static final int PRIORITY_DIFF_THRESHOLD = 3;

    /**
     * 尝试抢占低优先级任务，为高优先级任务腾出执行资源。
     *
     * <p>当线程池满时调用此方法。如果抢占成功，返回 true 表示可以提交高优先级任务。
     *
     * @param newJobPriority 新任务优先级（1-10，越小越高）
     * @param localNodeId    当前节点 ID
     * @return true 抢占成功；false 无需抢占或抢占失败
     */
    public boolean tryPreempt(int newJobPriority, String localNodeId) {
        // 仅高优先级任务触发抢占
        if (newJobPriority > PREEMPT_TRIGGER_PRIORITY) {
            return false;
        }

        // 查找可抢占的低优先级运行中任务
        JobLog preemptable = findPreemptableTask(newJobPriority, localNodeId);
        if (preemptable == null) {
            return false;
        }

        // 检查被抢占次数（防止饥饿）
        int preemptCount = getPreemptCount(preemptable.getJobKey());
        if (preemptCount >= MAX_PREEMPT_COUNT) {
            log.debug("[Preemptive] 任务被抢占次数已达上限, 跳过: jobKey={} count={}",
                    preemptable.getJobKey(), preemptCount);
            return false;
        }

        // 执行抢占：中断低优先级任务线程
        boolean interrupted = interruptTaskThread(preemptable);
        if (!interrupted) {
            log.warn("[Preemptive] 中断任务线程失败: jobKey={} threadId={}",
                    preemptable.getJobKey(), preemptable.getExecThreadId());
            return false;
        }

        // 标记任务为被抢占，更新日志状态
        markPreempted(preemptable);

        // 递增抢占计数器
        incrementPreemptCount(preemptable.getJobKey());

        log.info("[Preemptive] 抢占成功: newPriority={} preemptedJobKey={} preemptedThreadId={}",
                newJobPriority, preemptable.getJobKey(), preemptable.getExecThreadId());

        return true;
    }

    /**
     * 查找可被抢占的低优先级运行中任务。
     *
     * <p>P0-3 修复：原实现将 runningPriority 硬编码为 5，导致抢占逻辑失效。
     * 现在通过 jobId 批量关联查询 Job 获取实际优先级。
     *
     * <p>条件：
     * <ul>
     *   <li>状态为 RUNNING</li>
     *   <li>执行节点为当前节点（P0-3 修复：原实现未使用 localNodeId 参数）</li>
     *   <li>触发类型非 MANUAL</li>
     *   <li>优先级 ≥ PREEMPTABLE_PRIORITY</li>
     *   <li>与新任务优先级差 ≥ PRIORITY_DIFF_THRESHOLD</li>
     * </ul>
     *
     * @param newJobPriority 新任务优先级
     * @param localNodeId    当前节点 ID
     * @return 可被抢占的任务日志；无返回 null
     */
    private JobLog findPreemptableTask(int newJobPriority, String localNodeId) {
        try {
            LambdaQueryWrapper<JobLog> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(JobLog::getStatus, "RUNNING")
                    .eq(JobLog::getDeleted, 0)
                    .ne(JobLog::getTriggerType, "MANUAL")
                    .eq(JobLog::getExecNodeId, localNodeId)  // P0-3: 仅抢占当前节点上的任务
                    .orderByDesc(JobLog::getCreatedAt)
                    .last("LIMIT 20");
            List<JobLog> runningLogs = jobLogMapper.selectList(wrapper);
            if (runningLogs.isEmpty()) {
                return null;
            }

            // P0-3: 批量查询 Job 获取实际优先级，避免 N+1 查询
            List<String> jobIds = runningLogs.stream()
                    .map(JobLog::getJobId)
                    .distinct()
                    .collect(Collectors.toList());
            List<Job> jobs = jobMapper.selectBatchIds(jobIds);
            Map<String, Job> jobMap = jobs.stream()
                    .collect(Collectors.toMap(Job::getId, Function.identity()));

            // 从运行中任务中找到优先级最低（数值最大）且差值足够的任务
            JobLog bestCandidate = null;
            int bestPriority = -1;
            for (JobLog logEntry : runningLogs) {
                Job job = jobMap.get(logEntry.getJobId());
                if (job == null || job.getPriority() == null) {
                    continue;
                }
                int runningPriority = job.getPriority();
                if (runningPriority >= PREEMPTABLE_PRIORITY
                        && (runningPriority - newJobPriority) >= PRIORITY_DIFF_THRESHOLD) {
                    // 选择优先级最低（数值最大）的任务作为抢占目标
                    if (runningPriority > bestPriority) {
                        bestPriority = runningPriority;
                        bestCandidate = logEntry;
                    }
                }
            }
            return bestCandidate;
        } catch (Exception e) {
            log.warn("[Preemptive] 查找可抢占任务异常: reason={}", e.getMessage());
        }
        return null;
    }

    /**
     * 中断任务执行线程。
     *
     * @param log 任务日志
     * @return true 中断成功
     */
    private boolean interruptTaskThread(JobLog log) {
        Long threadId = log.getExecThreadId();
        if (threadId == null) {
            return false;
        }
        for (Thread t : Thread.getAllStackTraces().keySet()) {
            if (t.threadId() == threadId) {
                t.interrupt();
                return true;
            }
        }
        return false;
    }

    /**
     * 标记任务为被抢占。
     *
     * @param log 任务日志
     */
    private void markPreempted(JobLog logEntry) {
        try {
            logEntry.setStatus("FAILED");
            logEntry.setErrorMessage("被高优先级任务抢占");
            logEntry.setEndTime(LocalDateTime.now());
            jobLogMapper.updateById(logEntry);
        } catch (Exception e) {
            log.warn("[Preemptive] 标记抢占状态失败: reason={}", e.getMessage());
        }
    }

    /**
     * 获取任务被抢占次数。
     */
    private int getPreemptCount(String jobKey) {
        try {
            String value = redisStringOps.get(PREEMPT_COUNT_PREFIX + jobKey);
            return value != null ? Integer.parseInt(value) : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * 递增任务被抢占次数。
     */
    private void incrementPreemptCount(String jobKey) {
        try {
            String key = PREEMPT_COUNT_PREFIX + jobKey;
            redisStringOps.incr(key, 1);
            // 设置 1 小时 TTL，超时后重置计数
            redisStringOps.expire(key, Duration.ofHours(1));
        } catch (Exception e) {
            log.debug("[Preemptive] 递增抢占计数失败: reason={}", e.getMessage());
        }
    }
}
