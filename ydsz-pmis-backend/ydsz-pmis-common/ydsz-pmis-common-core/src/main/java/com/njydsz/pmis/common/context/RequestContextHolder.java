package com.njydsz.pmis.common.context;

import com.alibaba.ttl.TransmittableThreadLocal;
import lombok.extern.slf4j.Slf4j;

/**
 * 请求上下文清理守卫
 *
 * <p>自动清理 {@link RequestContext} 中的 TTL 上下文，防止线程池复用导致上下文泄漏。
 *
 * <h3>使用方式</h3>
 * <pre>{@code
 * try (var guard = new RequestContextHolder()) {
 *     RequestContext.set(ctx);
 *     // 业务逻辑
 * } // 自动清理
 * }</pre>
 *
 * <p>或使用静态方法：
 * <pre>{@code
 * RequestContext.set(ctx);
 * try {
 *     // 业务逻辑
 * } finally {
 *     RequestContextHolder.cleanup();
 * }
 * }</pre>
 *
 * @author ydsz-pmis-team
 * @since 1.5.0
 */
@Slf4j
public final class RequestContextHolder implements AutoCloseable {

    /** 标记是否已初始化（用于 try-with-resources 模式） */
    private final boolean initialized;

    /**
     * 创建清理守卫（用于 try-with-resources）
     */
    public RequestContextHolder() {
        this.initialized = true;
    }

    /**
     * 静态清理方法
     */
    public static void cleanup() {
        RequestContext.clear();
    }

    @Override
    public void close() {
        if (initialized) {
            cleanup();
        }
    }
}
