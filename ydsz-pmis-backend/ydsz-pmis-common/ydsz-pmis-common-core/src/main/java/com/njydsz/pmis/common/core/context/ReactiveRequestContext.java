package com.njydsz.pmis.common.core.context;

import com.alibaba.ttl.TransmittableThreadLocal;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;
import reactor.util.context.ContextView;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Reactor 响应式场景下的 RequestContext 桥接工具
 *
 * <p>WebFlux / R2DBC 场景下，线程切换频繁，需通过 {@code reactor.util.context.Context}
 * 传递请求上下文。本类提供 Mono/Flux 的 context 写入与读取能力，
 * 底层使用 {@link TransmittableThreadLocal} 作为兜底。</p>
 *
 * <p><b>使用示例：</b></p>
 * <pre>{@code
 * // 1. 在过滤器/拦截器中初始化响应式上下文
 * Mono.deferContextual(ctx -> {
 *     ReactiveRequestContext.bindContext(ctx);
 *     return processRequest();
 * });
 *
 * // 2. 在业务链中获取
 * Mono.just(1).map(i -> {
 *     String userId = ReactiveRequestContext.getUserId();
 *     return i;
 * });
 *
 * // 3. 完成后清理
 * Mono.deferContextual(ctx -> process()
 *     .transform(ReactiveRequestContext.clearOnTerminate()));
 * }</pre>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 3.5.0
 */
public final class ReactiveRequestContext {

    /** 响应式 Context 中的 key 标识 */
    public static final String CONTEXT_KEY = "REMI_REQUEST_CONTEXT";

    /** 兜底 TTL，避免响应式链不在订阅时丢失上下文 */
    private static final ThreadLocal<Map<String, Object>> FALLBACK = new TransmittableThreadLocal<>();

    private ReactiveRequestContext() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 将响应式 Context 写入到当前线程的兜底 TTL
     *
     * @param ctxView 响应式 ContextView
     */
    public static void bindContext(ContextView ctxView) {
        if (ctxView != null && ctxView.hasKey(CONTEXT_KEY)) {
            Map<String, Object> map = ctxView.get(CONTEXT_KEY);
            FALLBACK.set(map);
        }
    }

    /**
     * 清理当前线程的兜底上下文
     */
    public static void clear() {
        FALLBACK.remove();
    }

    /**
     * 将当前线程的 RequestContext 快照写入响应式 Context
     *
     * @return 增强后的 Mono
     * @param <T> 元素类型
     */
    public static <T> Mono<T> withContext(Mono<T> mono) {
        return Mono.deferContextual(ctxView -> {
            bindContext(ctxView);
            return mono;
        });
    }

    /**
     * 将当前线程的 RequestContext 快照写入响应式 Context
     *
     * @return 增强后的 Flux
     * @param <T> 元素类型
     */
    public static <T> Flux<T> withContext(Flux<T> flux) {
        return Flux.deferContextual(ctxView -> {
            bindContext(ctxView);
            return flux;
        });
    }

    /**
     * 创建写入上下文的 Mono 装饰器
     *
     * @param transformer 业务处理函数
     * @param <T>         元素类型
     * @return 装饰器函数
     */
    public static <T> Function<Mono<T>, Mono<T>> enrich() {
        Map<String, Object> snapshot = RequestContext.snapshot();
        return mono -> mono.contextWrite(Context.of(CONTEXT_KEY, snapshot));
    }

    /**
     * 在响应式流终止时清理当前线程的兜底上下文
     *
     * @param <T> 元素类型
     * @return 装饰器函数
     */
    public static <T> Function<Mono<T>, Mono<T>> clearOnTerminate() {
        return mono -> mono.doOnTerminate(ReactiveRequestContext::clear)
                .doOnError(err -> ReactiveRequestContext.clear());
    }

    /**
     * 从兜底上下文获取 userId
     *
     * @return userId，可能为 null
     */
    public static String getUserId() {
        Map<String, Object> map = FALLBACK.get();
        return map == null ? null : (String) map.get(RequestContext.KEY_USER_ID);
    }

    /**
     * 从兜底上下文获取 tenantId
     *
     * @return tenantId，可能为 null
     */
    public static String getTenantId() {
        Map<String, Object> map = FALLBACK.get();
        return map == null ? null : (String) map.get(RequestContext.KEY_TENANT_ID);
    }

    /**
     * 从兜底上下文获取 traceId
     *
     * @return traceId，可能为 null
     */
    public static String getTraceId() {
        Map<String, Object> map = FALLBACK.get();
        return map == null ? null : (String) map.get(RequestContext.KEY_TRACE_ID);
    }

    /**
     * 克隆当前 RequestContext 快照到新 Map
     *
     * @return 不可修改的 Map
     */
    public static Map<String, Object> snapshot() {
        Map<String, Object> map = FALLBACK.get();
        return map == null ? new HashMap<>() : new HashMap<>(map);
    }
}
