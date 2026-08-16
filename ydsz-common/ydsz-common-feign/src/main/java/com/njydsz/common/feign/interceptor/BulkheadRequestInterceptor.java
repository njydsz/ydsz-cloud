package com.njydsz.common.feign.interceptor;

import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import com.alibaba.ttl.TransmittableThreadLocal;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.njydsz.common.feign.config.FeignProperties;

/**
 * Feign 请求并发隔离（Bulkhead）拦截器。
 *
 * <p>使用信号量隔离模式，按服务维度限制最大并发请求数。
 * 当并发请求超过限制时，快速失败而非排队等待，防止某个下游服务变慢
 * 耗尽连接池资源影响其他服务。
 *
 * <p><b>许可释放机制：</b>
 * <ul>
 *   <li>{@link #apply(RequestTemplate)} 在请求发起前获取信号量许可，并通过 {@link #currentServiceName}
 *       ThreadLocal 记录当前线程持有的服务名；</li>
 *   <li>{@link #releaseCurrentPermit()} 在 Feign 调用完成（无论成功或失败）后释放许可，
 *       通常由 {@link FeignResponseInterceptor} 在 finally 块中调用；</li>
 *   <li>若 {@link #apply} 因信号量耗尽或线程中断抛出异常，<b>不会</b>写入 ThreadLocal，
 *       因此 {@link #releaseCurrentPermit()} 不会误释放许可。</li>
 * </ul>
 *
 * <p><b>配置示例（YAML）：</b>
 * <pre>
 * ydsz:
 *   feign:
 *     bulkhead:
 *       enabled: true
 *       default-max-concurrent-calls: 50
 *       acquire-timeout-millis: 100
 *       client-config:
 *         message:
 *           max-concurrent-calls: 10
 *         user:
 *           max-concurrent-calls: 100
 * </pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class BulkheadRequestInterceptor implements RequestInterceptor {

    private static final Logger log = LoggerFactory.getLogger(BulkheadRequestInterceptor.class);

    /** 默认最大并发请求数 */
    private static final int DEFAULT_MAX_CONCURRENT = 50;

    /** 默认获取信号量超时时间（毫秒） */
    private static final long DEFAULT_ACQUIRE_TIMEOUT_MS = 100;

    /** 按服务名隔离的信号量缓存 */
    private final ConcurrentHashMap<String, Semaphore> bulkheads = new ConcurrentHashMap<>();

    /** 按服务名定制的最大并发数（未配置的服务使用 defaultMaxConcurrent） */
    private final Map<String, Integer> perClientMaxConcurrent;

    private final int defaultMaxConcurrent;
    private final long acquireTimeoutMillis;

    /** 记录每个信号量创建时的最大并发数，用于检测配置变更 */
    private final ConcurrentHashMap<String, Integer> semaphoreMaxPermits = new ConcurrentHashMap<>();

    /**
     * 当前线程已获取许可的服务名（用于在调用完成后释放许可）。
     *
     * <p>仅在 {@link #apply} 成功获取许可后写入，调用 {@link #releaseCurrentPermit} 后清除。
     * 使用 {@link TransmittableThreadLocal} 而非裸 {@link ThreadLocal}，
     * 在业务方使用 {@code TtlExecutors.getTtlExecutorService} 包装线程池的场景下，
     * 服务名上下文能跨线程池传递，避免异步调用时许可泄漏。
     */
    private final TransmittableThreadLocal<String> currentServiceName = new TransmittableThreadLocal<>();

    /**
     * 使用默认最大并发数（50）和默认获取超时（100ms）构造。
     */
    public BulkheadRequestInterceptor() {
        this(DEFAULT_MAX_CONCURRENT, DEFAULT_ACQUIRE_TIMEOUT_MS, null);
    }

    /**
     * 使用自定义默认最大并发数构造。
     *
     * @param defaultMaxConcurrent 默认最大并发请求数
     */
    public BulkheadRequestInterceptor(int defaultMaxConcurrent) {
        this(defaultMaxConcurrent, DEFAULT_ACQUIRE_TIMEOUT_MS, null);
    }

    /**
     * 使用完整配置构造。
     *
     * @param defaultMaxConcurrent     默认最大并发请求数
     * @param acquireTimeoutMillis     获取信号量超时时间（毫秒）
     * @param perClientMaxConcurrent   按服务名定制的最大并发数映射（可为 null）
     */
    public BulkheadRequestInterceptor(int defaultMaxConcurrent, long acquireTimeoutMillis,
                                       Map<String, Integer> perClientMaxConcurrent) {
        this.defaultMaxConcurrent = defaultMaxConcurrent > 0 ? defaultMaxConcurrent : DEFAULT_MAX_CONCURRENT;
        this.acquireTimeoutMillis = acquireTimeoutMillis > 0 ? acquireTimeoutMillis : DEFAULT_ACQUIRE_TIMEOUT_MS;
        this.perClientMaxConcurrent = perClientMaxConcurrent != null
                ? Collections.unmodifiableMap(new HashMap<>(perClientMaxConcurrent))
                : Collections.emptyMap();
    }

    /**
     * 从 {@link FeignProperties.Bulkhead} 配置构造。
     *
     * @param config Bulkhead 配置属性
     */
    public BulkheadRequestInterceptor(FeignProperties.Bulkhead config) {
        this(config.getDefaultMaxConcurrent(),
             config.getAcquireTimeoutMs(),
             config.getServiceMaxConcurrent());
    }

    /**
     * 在 Feign 请求发起前获取信号量许可，实现请求并发隔离。
     *
     * <p>按服务维度获取信号量，超过最大并发数时快速失败。
     * 成功获取许可后，将服务名写入 ThreadLocal 供后续释放。
     *
     * @param requestTemplate Feign 请求模板
     */
    @Override
    public void apply(RequestTemplate requestTemplate) {
        String serviceName = extractServiceName(requestTemplate);
        int maxConcurrent = resolveMaxConcurrent(serviceName);

        // 获取或创建信号量，如果配置变更则重建
        Semaphore semaphore = getOrCreateSemaphore(serviceName, maxConcurrent);

        try {
            if (!semaphore.tryAcquire(acquireTimeoutMillis, TimeUnit.MILLISECONDS)) {
                log.warn("[Bulkhead] 服务 {} 并发请求超限({}), 快速失败", serviceName, maxConcurrent);
                throw new RuntimeException("Bulkhead full for service: " + serviceName
                        + ", max concurrent: " + maxConcurrent);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Thread interrupted while acquiring bulkhead permit for: " + serviceName, e);
        }

        // 成功获取许可后，记录服务名供后续释放
        currentServiceName.set(serviceName);
        requestTemplate.header("X-Bulkhead-Acquired", serviceName);
    }

    /**
     * 获取或创建信号量，当配置的最大并发数发生变更时自动重建。
     *
     * <p>热更新机制：比较当前配置值与创建信号量时的值，若不一致则创建新信号量替换旧值。
     * 注意：重建信号量时旧信号量中已持有的许可不会被迁移，需等待自然释放后新请求才能获取新配额。
     *
     * @param serviceName   服务名称
     * @param maxConcurrent 当前配置的最大并发数
     * @return 与配置匹配的信号量实例
     */
    private Semaphore getOrCreateSemaphore(String serviceName, int maxConcurrent) {
        Semaphore current = bulkheads.get(serviceName);
        if (current != null) {
            Integer previousMax = semaphoreMaxPermits.get(serviceName);
            if (previousMax != null && previousMax == maxConcurrent) {
                return current;
            }
            // 配置已变更，需要重建信号量（旧信号量等待自然释放后可被 GC）
            log.info("[Bulkhead] 服务 {} 并发配置从 {} 变更为 {}, 重建信号量",
                    serviceName, previousMax, maxConcurrent);
        }
        Semaphore created = new Semaphore(maxConcurrent);
        bulkheads.put(serviceName, created);
        semaphoreMaxPermits.put(serviceName, maxConcurrent);
        return created;
    }

    /**
     * 释放当前线程持有的 Bulkhead 许可。
     *
     * <p>必须在 Feign 调用完成（无论成功或失败）后在 finally 块中调用，
     * 否则会导致信号量永久占用，最终所有请求都被拒绝。
     *
     * <p>若当前线程未持有许可（例如 {@link #apply} 抛出异常未获取许可），
     * 此方法为空操作，不会误释放。
     */
    public void releaseCurrentPermit() {
        String serviceName = currentServiceName.get();
        if (serviceName == null) {
            return;
        }
        currentServiceName.remove();
        Semaphore semaphore = bulkheads.get(serviceName);
        if (semaphore != null) {
            semaphore.release();
        }
    }

    /**
     * 释放指定服务的信号量许可（向后兼容旧 API）。
     *
     * <p>推荐使用 {@link #releaseCurrentPermit()} 自动管理 ThreadLocal。
     * 此方法仅在调用方明确知道服务名时使用。
     *
     * @param serviceName 服务名称
     */
    public void releasePermit(String serviceName) {
        Semaphore semaphore = bulkheads.get(serviceName);
        if (semaphore != null) {
            semaphore.release();
        }
    }

    /**
     * 获取指定服务的当前可用许可数。
     *
     * @param serviceName 服务名称
     * @return 可用许可数，-1 表示未初始化
     */
    public int getAvailablePermits(String serviceName) {
        Semaphore semaphore = bulkheads.get(serviceName);
        return semaphore != null ? semaphore.availablePermits() : -1;
    }

    /**
     * 解析指定服务的最大并发数。
     *
     * @param serviceName 服务名称
     * @return 该服务的最大并发数（优先使用 perClient 配置，否则使用默认值）
     */
    private int resolveMaxConcurrent(String serviceName) {
        Integer custom = perClientMaxConcurrent.get(serviceName);
        if (custom != null && custom > 0) {
            return custom;
        }
        return defaultMaxConcurrent;
    }

    private String extractServiceName(RequestTemplate requestTemplate) {
        try {
            String url = requestTemplate.url();
            if (url != null && url.startsWith("http")) {
                String host = URI.create(url).getHost();
                if (host != null) {
                    return host;
                }
            }
            return requestTemplate.feignTarget() != null ? requestTemplate.feignTarget().name() : "default";
        } catch (Exception e) {
            return "default";
        }
    }
}
