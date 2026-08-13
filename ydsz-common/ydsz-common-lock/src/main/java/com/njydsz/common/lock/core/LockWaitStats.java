package com.njydsz.common.lock.core;

import java.util.concurrent.atomic.LongAdder;


/**
 * 锁等待统计数据
 *
 * <p>记录单个锁键的历史等待指标，供 {@link LockWaitTimePolicy} 动态调整等待时间。
 * 所有计数器均为非阻塞的原子操作，线程安全。
 *
 * <p>统计维度：
 * <ul>
 *   <li>总等待次数与总等待耗时（用于计算平均值）</li>
 *   <li>超时次数（用于衡量等待策略有效性）</li>
 *   <li>成功获取锁的平均耗时</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see LockWaitTimePolicy
 */
public class LockWaitStats {

    /** 累计等待次数 */
    private final LongAdder totalWaitCount = new LongAdder();

    /** 累计等待耗时（毫秒） */
    private final LongAdder totalWaitMillis = new LongAdder();

    /** 累计超时次数（等待到期仍未获取锁） */
    private final LongAdder totalTimeoutCount = new LongAdder();

    /** 累计成功获取锁前的等待耗时（毫秒） */
    private final LongAdder totalSuccessWaitMillis = new LongAdder();

    /** 累计成功获取锁次数 */
    private final LongAdder totalSuccessCount = new LongAdder();

    /**
     * 记录一次等待尝试（无论成功或超时）
     *
     * @param waitMillis 实际等待耗时（毫秒）
     * @param timeout    是否因超时失败
     */
    public void recordWait(long waitMillis, boolean timeout) {
        totalWaitCount.increment();
        totalWaitMillis.add(waitMillis);
        if (timeout) {
            totalTimeoutCount.increment();
        } else {
            totalSuccessWaitMillis.add(waitMillis);
            totalSuccessCount.increment();
        }
    }

    /**
     * 获取总等待次数
     *
     * @return 累计等待次数
     */
    public long getTotalWaitCount() {
        return totalWaitCount.sum();
    }

    /**
     * 获取平均等待耗时（毫秒）
     *
     * @return 平均等待耗时，无记录时返回 0
     */
    public double getAverageWaitMillis() {
        long count = totalWaitCount.sum();
        return count == 0 ? 0.0 : (double) totalWaitMillis.sum() / count;
    }

    /**
     * 获取超时率
     *
     * @return 超时率（0.0 ~ 1.0），无记录时返回 0
     */
    public double getTimeoutRate() {
        long count = totalWaitCount.sum();
        return count == 0 ? 0.0 : (double) totalTimeoutCount.sum() / count;
    }

    /**
     * 获取成功获取锁的平均等待耗时（毫秒）
     *
     * @return 成功场景下的平均等待耗时，无记录时返回 0
     */
    public double getAverageSuccessWaitMillis() {
        long successCount = totalSuccessCount.sum();
        return successCount == 0 ? 0.0 : (double) totalSuccessWaitMillis.sum() / successCount;
    }

    /**
     * 重置所有统计数据
     */
    public void reset() {
        totalWaitCount.reset();
        totalWaitMillis.reset();
        totalTimeoutCount.reset();
        totalSuccessWaitMillis.reset();
        totalSuccessCount.reset();
    }
}
