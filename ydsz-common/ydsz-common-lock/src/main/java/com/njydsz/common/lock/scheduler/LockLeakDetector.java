package com.njydsz.common.lock.scheduler;

import java.util.Map;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;

import com.njydsz.common.lock.metrics.LockMetrics;

import lombok.extern.slf4j.Slf4j;

/**
 * 锁泄漏检测器
 *
 * <p>周期扫描 {@link LockWatchDog} 中活跃的续期任务，检测长时间持有未释放的锁。
 *
 * <p><b>检测逻辑：</b>
 * <ul>
 *   <li>每 60 秒扫描一次活跃续期任务</li>
 *   <li>当某把锁的续期次数超过最大续期限制的 80% 时，记录 WARN 日志</li>
 *   <li>当某把锁的续期次数达到最大续期限制时，记录 ERROR 日志</li>
 *   <li>可选上报指标到 LockMetrics</li>
 * </ul>
 *
 * <p><b>使用场景：</b>
 * <ul>
 *   <li>业务代码异常导致 unlock 未执行</li>
 *   <li>死循环或长时间阻塞操作导致锁持有时间异常</li>
 *   <li>看门狗持续续期但业务未完成</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public class LockLeakDetector {

    private final ObjectProvider<LockWatchDog> watchDogProvider;
    private final ObjectProvider<LockMetrics> lockMetricsProvider;

    public LockLeakDetector(ObjectProvider<LockWatchDog> watchDogProvider,
                             ObjectProvider<LockMetrics> lockMetricsProvider) {
        this.watchDogProvider = watchDogProvider;
        this.lockMetricsProvider = lockMetricsProvider;
    }

    /**
     * 每 60 秒扫描一次活跃续期任务，检测锁泄漏
     *
     * <p>扫描 {@link LockWatchDog} 中所有活跃的续期任务，对续期次数接近最大限制的锁发出告警。
     */
    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void detectLockLeak() {
        LockWatchDog watchDog = watchDogProvider.getIfAvailable();
        if (watchDog == null) {
            return;
        }
        int maxRenewTimes = watchDog.getMaxRenewTimes();
        int warningThreshold = (int) (maxRenewTimes * 0.8);

        Map<String, LockWatchDog.WatchTask> activeTasks = watchDog.getActiveTasksSnapshot();
        if (activeTasks == null || activeTasks.isEmpty()) {
            return;
        }

        LockMetrics metrics = lockMetricsProvider.getIfAvailable();

        for (Map.Entry<String, LockWatchDog.WatchTask> entry : activeTasks.entrySet()) {
            String lockKey = entry.getKey();
            LockWatchDog.WatchTask task = entry.getValue();
            int renewCount = task.getRenewCount();

            if (renewCount >= maxRenewTimes) {
                log.error("[ydsz-lock] [leak]锁续期次数已达最大限制，可能泄漏 | lockKey={} | renewCount={}/{} | leaseTime={}ms",
                        lockKey, renewCount, maxRenewTimes, task.getLeaseTime());
            } else if (renewCount >= warningThreshold) {
                log.warn("[ydsz-lock] [leak]锁续期次数接近最大限制，可能泄漏 | lockKey={} | renewCount={}/{} | leaseTime={}ms",
                        lockKey, renewCount, maxRenewTimes, task.getLeaseTime());
            }
        }

        if (metrics != null && !activeTasks.isEmpty()) {
            log.debug("[ydsz-lock] [leak]活跃续期任务数={} | maxRenewTimes={}", activeTasks.size(), maxRenewTimes);
        }
    }
}
