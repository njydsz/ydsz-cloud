package com.njydsz.common.thread.config;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

/**
 * 统一线程池配置属性。
 *
 * <p>支持按业务隔离配置多个线程池，每个线程池独立的 coreSize/maxSize/queue/rejectPolicy 等参数。
 *
 * <h3>配置示例</h3>
 * <pre>
 * ydsz:
 *   thread:
 *     enabled: true
 *     pools:
 *       io:
 *         core-size: 8
 *         max-size: 32
 *         queue-capacity: 200
 *         thread-name-prefix: ydsz-io-
 *         reject-policy: CALLER_RUNS
 *         await-termination-seconds: 60
 *       cpu:
 *         core-size: 4
 *         max-size: 4
 *         queue-capacity: 100
 *         thread-name-prefix: ydsz-cpu-
 *         reject-policy: ABORT
 * </pre>
 *
 * <p>注入方式：<br>
 * {@code @Resource(name = "ioExecutor") private ThreadPoolTaskExecutor ioExecutor;}
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@ConfigurationProperties(prefix = "ydsz.thread")
public class ThreadPoolProperties {

    /**
     * 是否启用统一线程池管理（默认 true）。
     */
    private boolean enabled = true;

    /**
     * 线程池配置映射，key 为线程池名称（如 io、cpu、batch），Bean 名称为 key + "Executor"。
     */
    private Map<String, PoolConfig> pools = new LinkedHashMap<>();

    /**
     * 单个线程池配置。
     */
    @Data
    public static class PoolConfig {

        /**
         * 核心线程数（默认 2）。
         */
        private int coreSize = 2;

        /**
         * 最大线程数（默认 8）。
         */
        private int maxSize = 8;

        /**
         * 阻塞队列容量（默认 100）。
         */
        private int queueCapacity = 100;

        /**
         * 线程名前缀（默认 ydsz-thread-）。
         */
        private String threadNamePrefix = "ydsz-thread-";

        /**
         * 拒绝策略（默认 CALLER_RUNS）。
         */
        private RejectPolicy rejectPolicy = RejectPolicy.CALLER_RUNS;

        /**
         * 优雅关闭等待秒数（默认 60）。
         */
        private int awaitTerminationSeconds = 60;

        /**
         * 是否允许核心线程超时回收（默认 false）。
         */
        private boolean allowCoreThreadTimeOut = false;

        /**
         * 线程空闲存活秒数（默认 60）。
         */
        private int keepAliveSeconds = 60;
    }

    /**
     * 拒绝策略枚举。
     */
    public enum RejectPolicy {
        /** 抛出 RejectedExecutionException */
        ABORT,
        /** 由调用线程执行 */
        CALLER_RUNS,
        /** 丢弃队列最旧任务 */
        DISCARD_OLDEST,
        /** 静默丢弃 */
        DISCARD
    }
}
