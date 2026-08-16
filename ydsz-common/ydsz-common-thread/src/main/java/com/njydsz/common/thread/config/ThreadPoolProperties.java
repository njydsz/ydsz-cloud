package com.njydsz.common.thread.config;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import com.njydsz.common.thread.metrics.ThreadPoolMetrics;
import com.njydsz.common.thread.metrics.VirtualThreadMetrics;

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
 *         metric-prefix: ydsz.executor
 *         slow-task-threshold-ms: 3000
 *         task-decorator-bean-names:
 *           - mdcTaskDecorator
 *           - requestContextTaskDecorator
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
 * <p>v1.3.0 变更：新增 {@code metric-prefix} 和 {@code task-decorator-bean-names} 配置项。
 *
 * <p>v1.4.0 变更：
 * <ul>
 *   <li>新增 {@code slow-task-threshold-ms} 配置项，支持自定义慢任务阈值</li>
 *   <li>新增 {@code hot-update} 配置项，支持自动注册热更新监听器</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@Validated
@ConfigurationProperties(prefix = "ydsz.thread")
public class ThreadPoolProperties {

    /**
     * 是否启用统一线程池管理（默认 true）。
     */
    private boolean enabled = true;

    /**
     * 热更新配置。默认不启用。
     * <p>启用后，应用启动时会自动注册热更新监听器，允许运行时动态调整线程池参数。
     *
     * @since 1.4.0
     */
    private HotUpdateConfig hotUpdate = new HotUpdateConfig();

    /**
     * Bean 名称前缀（默认空字符串）。
     * <p>设置后所有线程池 Bean 名称将变为 {@code beanNamePrefix + key + "Executor"}。
     * 建议新部署使用 {@code "ydsz-"} 前缀以避免与业务 Bean 命名冲突。
     * <p>示例：prefix = "ydsz-", key = "io" → Bean 名称为 "ydsz-ioExecutor"。
     *
     * @since 1.3.0
     */
    private String beanNamePrefix = "";

    /**
     * 线程池配置映射，key 为线程池名称（如 io、cpu、batch），Bean 名称为 beanNamePrefix + key + "Executor"。
     */
    private Map<String, PoolConfig> pools = new LinkedHashMap<>();

    /**
     * 单个线程池配置。
     */
    @Data
    @Validated
    public static class PoolConfig {

        /**
         * 线程池类型：PLATFORM（平台线程，默认）或 VIRTUAL（虚拟线程，JDK 21+）。
         * <p>虚拟线程池不受 coreSize/maxSize/queueCapacity 限制，
         * 每个任务分配一个虚拟线程，适合 IO 密集型场景。
         */
        private PoolType type = PoolType.PLATFORM;

        /**
         * 核心线程数（默认 2，最小 1）。
         * <p>仅对 PLATFORM 类型生效。
         */
        @Min(value = 1, message = "coreSize 必须 >= 1")
        private int coreSize = 2;

        /**
         * 最大线程数（默认 8，最小 1）。
         * <p>必须大于等于 coreSize。仅对 PLATFORM 类型生效。
         */
        @Min(value = 1, message = "maxSize 必须 >= 1")
        private int maxSize = 8;

        /**
         * 阻塞队列容量（默认 100，最小 0；0 表示 SynchronousQueue）。
         * <p>仅对 PLATFORM 类型生效。
         */
        @Min(value = 0, message = "queueCapacity 必须 >= 0")
        private int queueCapacity = 100;

        /**
         * 线程名前缀（默认 ydsz-thread-）。
         */
        private String threadNamePrefix = "ydsz-thread-";

        /**
         * 拒绝策略（默认 CALLER_RUNS）。
         * <p>仅对 PLATFORM 类型生效。
         */
        private RejectPolicy rejectPolicy = RejectPolicy.CALLER_RUNS;

        /**
         * 优雅关闭等待秒数（默认 60，最小 0）。
         */
        @Min(value = 0, message = "awaitTerminationSeconds 必须 >= 0")
        private int awaitTerminationSeconds = 60;

        /**
         * 是否允许核心线程超时回收（默认 false）。
         * <p>仅对 PLATFORM 类型生效。
         */
        private boolean allowCoreThreadTimeOut = false;

        /**
         * 线程空闲存活秒数（默认 60，最小 0）。
         * <p>仅对 PLATFORM 类型生效。
         */
        @Min(value = 0, message = "keepAliveSeconds 必须 >= 0")
        @Max(value = 3600, message = "keepAliveSeconds 建议不超过 3600 秒")
        private int keepAliveSeconds = 60;

        /**
         * 指标前缀（仅对 PLATFORM 类型生效）。
         * <p>默认 {@link ThreadPoolMetrics#DEFAULT_METRIC_PREFIX}。
         * 仅在您想自定义指标名称前缀时设置。
         * <p>虚拟线程池固定使用 {@link VirtualThreadMetrics#DEFAULT_METRIC_PREFIX} 前缀。
         *
         * @since 1.3.0
         */
        private String metricPrefix = ThreadPoolMetrics.DEFAULT_METRIC_PREFIX;

        /**
         * 慢任务阈值毫秒数（默认 5000，最小 100）。
         * <p>任务执行耗时超过此阈值时，慢任务计数器 {@code ydsz.executor.slow.tasks} 递增。
         * 默认值 5000ms 适用于大多数 IO 密集场景；AI Agent 等长耗时场景建议设置为 30000。
         *
         * @since 1.4.0
         */
        @Min(value = 100, message = "slowTaskThresholdMs 必须 >= 100")
        private long slowTaskThresholdMs = 5000L;

        /**
         * 是否启用详细指标（默认 false）。
         * <p>启用后，除核心 5 项指标外，还会注册以下详细指标：
         * <ul>
         *   <li>{@code ydsz.executor.pool.max} - 线程池最大容量</li>
         *   <li>{@code ydsz.executor.queue.remaining} - 工作队列剩余容量</li>
         *   <li>{@code ydsz.executor.queue.usage} - 工作队列使用率（0.0 - 1.0）</li>
         * </ul>
         * <p>核心 5 项指标（active / pool.size / queue.size / completed / rejected）始终注册。
         * <p>耗时指标（execution / queue.wait Timer）和慢任务计数器由
         * {@code slow-task-threshold-ms} 控制，不受此选项影响。
         *
         * @since 1.4.0
         */
        private boolean enableDetailedMetrics = false;

        /**
         * TaskDecorator Bean 名称列表（仅对 PLATFORM 类型生效）。
         * <p>用于跨线程传播上下文，例如 MDC 日志追踪 ID、RequestContext、SecurityContext 等。
         * 配置的 Bean 名称对应的 Bean 必须实现 {@link org.springframework.core.task.TaskDecorator} 接口。
         *
         * @since 1.3.0
         */
        private List<String> taskDecoratorBeanNames;

        /**
         * 校验 maxSize 必须 >= coreSize。
         *
         * <p>仅对 PLATFORM 类型生效。虚拟线程池不受此限制。
         *
         * @return true 如果配置合法
         */
        @AssertTrue(message = "maxSize 必须 >= coreSize")
        public boolean isMaxSizeValid() {
            if (type != PoolType.PLATFORM) {
                return true;
            }
            return maxSize >= coreSize;
        }
    }

    /**
     * 线程池类型枚举。
     */
    public enum PoolType {
        /** 平台线程池（传统 ThreadPoolExecutor） */
        PLATFORM,
        /** 虚拟线程池（JDK 21+，每任务一线程） */
        VIRTUAL
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

    /**
     * 热更新配置属性。
     *
     * @since 1.4.0
     */
    @Data
    public static class HotUpdateConfig {

        /**
         * 是否启用热更新监听器（默认 false）。
         * <p>启用后，应用启动时自动打印线程池注册摘要，
         * 并提供运行时调整线程池参数的能力。
         */
        private boolean enabled = false;
    }
}
