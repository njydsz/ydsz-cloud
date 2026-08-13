package com.njydsz.common.lock.core;


/**
 * 锁等待时间策略接口
 *
 * <p>支持基于历史等待统计数据动态调整锁获取的等待超时时间。
 * 实现类可根据锁键的历史竞争情况（平均等待耗时、超时率等）智能决策最优等待时长，
 * 避免固定等待时间在竞争激烈场景下的大量超时浪费，或在低竞争场景下的过早放弃。
 *
 * <p>典型应用场景：
 * <ul>
 *   <li>竞争激烈场景：超时率持续升高，策略可逐步降低等待时间，快速失败让出资源</li>
 *   <li>竞争缓和场景：平均等待耗时短且超时率低，策略可适当延长等待时间</li>
 *   <li>锁键分级策略：对高优先级锁键赋予更长的等待时间预算</li>
 * </ul>
 *
 * <p>使用示例：
 * <pre>{@code
 * LockWaitTimePolicy policy = new AdaptiveWaitTimePolicy(
 *     Duration.ofSeconds(30),
 *     Duration.ofMillis(100)
 * );
 * // 在 tryLockWithWait 中调用
 * long adjustedWaitTime = policy.calculateWaitTime(lockKey, requestedWaitTime, stats);
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see LockWaitStats
 */
public interface LockWaitTimePolicy {

    /**
     * 计算动态等待时间
     *
     * <p>实现时应保证返回值满足以下约束：
     * <ul>
     *   <li>返回值 &gt;= 0（负值会被截断为 0）</li>
     *   <li>返回值 &lt;= requestedWaitTime（不超过用户请求的上限）</li>
     *   <li>当 stats 为 null 时退化为安全默认值（如直接返回 requestedWaitTime）</li>
     * </ul>
     *
     * @param lockKey          锁键（非空）
     * @param requestedWaitTime 用户请求的最大等待时间（毫秒，&gt; 0）
     * @param stats            该锁键的历史等待统计，首次调用时可能为 null
     * @return 调整后的等待时间（毫秒），应在 [0, requestedWaitTime] 范围内
     */
    long calculateWaitTime(String lockKey, long requestedWaitTime, LockWaitStats stats);
}
