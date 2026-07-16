package com.njydsz.common.feign.interceptor;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import feign.RequestInterceptor;
import feign.RequestTemplate;

/**
 * Feign 请求并发隔离（Bulkhead）拦截器。
 *
 * <p>使用信号量隔离模式，按服务维度限制最大并发请求数。
 * 当并发请求超过限制时，快速失败而非排队等待，防止某个下游服务变慢
 * 耗尽连接池资源影响其他服务。
 *
 * <p><b>配置示例（YAML）：</b>
 * <pre>
 * ydsz:
 *   feign:
 *     bulkhead:
 *       enabled: true
 *       default-max-concurrent-calls: 50
 *       client-config:
 *         slowService:
 *           max-concurrent-calls: 10
 *         fastService:
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

    /** 获取信号量超时时间（毫秒） */
    private static final long ACQUIRE_TIMEOUT_MS = 100;

    /** 按服务名隔离的信号量缓存 */
    private final ConcurrentHashMap<String, Semaphore> bulkheads = new ConcurrentHashMap<>();

    private final int defaultMaxConcurrent;

    /**
     * 使用默认最大并发数（50）构造。
     */
    public BulkheadRequestInterceptor() {
        this(DEFAULT_MAX_CONCURRENT);
    }

    /**
     * 使用自定义默认最大并发数构造。
     *
     * @param defaultMaxConcurrent 默认最大并发请求数
     */
    public BulkheadRequestInterceptor(int defaultMaxConcurrent) {
        this.defaultMaxConcurrent = defaultMaxConcurrent > 0 ? defaultMaxConcurrent : DEFAULT_MAX_CONCURRENT;
    }

    @Override
    public void apply(RequestTemplate requestTemplate) {
        String serviceName = extractServiceName(requestTemplate);
        Semaphore semaphore = bulkheads.computeIfAbsent(serviceName,
                k -> new Semaphore(defaultMaxConcurrent));

        try {
            if (!semaphore.tryAcquire(ACQUIRE_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                log.warn("[Bulkhead] 服务 {} 并发请求超限({}), 快速失败", serviceName, defaultMaxConcurrent);
                throw new RuntimeException("Bulkhead full for service: " + serviceName
                        + ", max concurrent: " + defaultMaxConcurrent);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Thread interrupted while acquiring bulkhead permit for: " + serviceName, e);
        }

        requestTemplate.header("X-Bulkhead-Acquired", serviceName);
    }

    /**
     * 释放信号量许可。
     *
     * <p>应在 Feign 调用完成后（无论成功或失败）调用此方法释放许可。
     * 通常由 {@link FeignResponseInterceptor} 在 finally 块中调用。
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

    private String extractServiceName(RequestTemplate requestTemplate) {
        try {
            String url = requestTemplate.url();
            if (url != null && url.startsWith("http")) {
                String host = java.net.URI.create(url).getHost();
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
