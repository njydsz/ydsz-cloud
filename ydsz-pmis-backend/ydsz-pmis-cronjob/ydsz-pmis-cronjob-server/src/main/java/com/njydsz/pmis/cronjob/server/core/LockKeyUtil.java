package com.njydsz.pmis.cronjob.server.core;

/**
 * 任务锁 key 构造工具（P0-11）。
 *
 * <p>集中管理任务锁 key 的构造逻辑，确保分片任务与非分片任务使用一致的 key 格式，
 * 避免不同 Scanner/Dispatcher 自行拼接导致的分片锁释放失败问题。
 *
 * <h3>锁 key 格式</h3>
 * <ul>
 *   <li>非分片任务：{@code pmis:job:lock:{jobKey}}</li>
 *   <li>分片任务：{@code pmis:job:lock:{jobKey}:shard:{shardIndex}}</li>
 * </ul>
 *
 * <p>所有需要构造或释放任务锁的组件（DefaultTaskDispatcher / FailoverScanner /
 * TimeoutMonitor / SelfHealingScanner / JobNodeReaper / JobServiceImpl 等）
 * 都应通过本工具类构造锁 key，禁止在业务代码中直接拼接。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public final class LockKeyUtil {

    /** 任务锁 key 前缀 */
    public static final String JOB_LOCK_PREFIX = "pmis:job:lock:";

    /** 分片锁后缀格式 */
    private static final String SHARD_SUFFIX_FORMAT = ":shard:%d";

    private LockKeyUtil() {
        // 工具类禁止实例化
    }

    /**
     * 构建任务锁 key（不含分片）。
     *
     * @param jobKey 任务 key，非空
     * @return 锁 key，如 {@code pmis:job:lock:order-sync}
     */
    public static String buildJobLockKey(String jobKey) {
        return JOB_LOCK_PREFIX + jobKey;
    }

    /**
     * 构建任务分片锁 key。
     *
     * <p>分片索引为 {@code null} 或负数时退化为普通任务锁 key，
     * 用于兼容非分片任务调用同一方法的场景。
     *
     * @param jobKey 任务 key，非空
     * @param shardIndex 分片索引（{@code null} 或 < 0 表示非分片任务）
     * @return 分片任务返回 {@code pmis:job:lock:{jobKey}:shard:{shardIndex}}，
     *         非分片任务返回 {@code pmis:job:lock:{jobKey}}
     */
    public static String buildJobLockKey(String jobKey, Integer shardIndex) {
        if (shardIndex == null || shardIndex < 0) {
            return JOB_LOCK_PREFIX + jobKey;
        }
        return JOB_LOCK_PREFIX + jobKey + String.format(SHARD_SUFFIX_FORMAT, shardIndex);
    }
}
