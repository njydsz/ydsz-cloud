package com.njydsz.pmis.common.lock.config;

import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 分布式锁配置属性类
 *
 * <p>配置值通过 application.yml 中的 remi.lock 前缀注入。
 *
 * <p><b>配置示例（application.yml）：</b>
 * <pre>{@code
 * remi:
 *   lock:
 *     enabled: true
 *     fallback-enabled: true
 *     max-renew-times: 100
 *     acquire-pool:
 *       core-size: 4
 *       max-size: 32
 *       queue-capacity: 256
 *     scheduler-pool-size: 2
 * }</pre>
 *
 * <p><b>兼容性说明：</b>
 * 当前配置前缀为 {@code remi.lock}，历史版本曾使用 {@code remi.distributed-lock} 前缀，
 * 已统一迁移至 {@code remi.lock}，旧前缀不再支持。
 *
 * @author ydsz-pmis-team
 * 
 * 
 * @since 1.0.0
 */
@Data
@Validated
@ConfigurationProperties(prefix = "remi.lock")
public class LockProperties {

    /**
     * 是否启用分布式锁功能（控制整个分布式锁模块的开关，默认开启）
     */
    private boolean enabled = true;

    /**
     * 是否启用锁降级策略（Redis 不可用时降级为本地 ReentrantLock）
     */
    private boolean fallbackEnabled = true;

    /**
     * WatchDog 最大续期次数（默认 100 次，约 30 分钟）
     * <p>续期次数超过限制后停止续期，锁自动过期，防止业务线程卡死导致锁永不释放
     */
    private int maxRenewTimes = 100;

    /**
     * 锁获取线程池配置
     */
    private ThreadPool acquirePool = new ThreadPool();

    /**
     * 调度线程池大小（用于 WatchDog 续期和信号量超时调度）
     */
    @Min(1)
    private int schedulerPoolSize = 2;

    /**
     * 是否启用 WatchDog 自动续期功能
     * <p>默认启用，设为 false 后加锁时不会启动续期任务，
     * 锁将在 leaseTime 到期后自动过期释放
     */
    private boolean watchdogEnabled = true;

    /**
     * 锁键命名空间前缀（通常使用 ${spring.application.name}）
     * <p>设置后，锁键会自动添加前缀：${namespace}:lock:${userKey}
     * <p>用于多应用共享 Redis 时的锁键隔离，避免不同应用间的锁键冲突
     */
    private String namespace;

    /**
     * 锁默认超时时间（秒），默认 30 秒
     */
    @Min(1)
    private int defaultLockTimeoutSeconds = 30;

    /**
     * 多 Key 联锁（RedisMultiLock）专用配置
     *
     * <p>当业务需要同时锁定多个资源时启用，框架内部使用 {@code RedisMultiLock} 实现。
     */
    private MultiLock multiLock = new MultiLock();

    @Data
    public static class ThreadPool {
        /** 核心线程数 */
        private int coreSize = 4;
        /** 最大线程数 */
        private int maxSize = 32;
        /** 队列容量 */
        private int queueCapacity = 256;
    }

    /**
     * 多 Key 联锁配置
     */
    @Data
    public static class MultiLock {
        /**
         * 多 Key 联锁最大续期次数，默认 30 次（即最长约 10 分钟）
         */
        @Min(1)
        private int maxRenewCount = 30;

        /**
         * 多 Key 联锁续期间隔（秒），默认 10 秒
         * <p>每次续期后等待此时间再次续期
         */
        @Min(1)
        private int renewIntervalSeconds = 10;
    }
}
