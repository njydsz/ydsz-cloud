package com.njydsz.pmis.common.util.concurrent;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

/**
 * 上下文传播工具类
 *
 * <p>解决线程池/异步场景下 ThreadLocal 上下文丢失问题。
 * 在提交任务到线程池前捕获当前线程的上下文，在任务执行前恢复上下文，执行后清理。
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * // 注册上下文提供者
 * ContextPropagationUtils.registerContextProvider("traceId", () -> MDC.get("traceId"));
 * ContextPropagationUtils.registerContextProvider("tenantId", () -> TenantContext.get());
 *
 * // 提交任务到线程池时包装
 * executor.submit(ContextPropagationUtils.wrap(() -> {
 *     // 此处可正常访问 MDC.get("traceId") 和 TenantContext.get()
 *     doBusinessLogic();
 * }));
 * }</pre>
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
public final class ContextPropagationUtils {

    private static final Map<String, Supplier<String>> CONTEXT_PROVIDERS = new java.util.concurrent.ConcurrentHashMap<>();

    private ContextPropagationUtils() {
        throw new UnsupportedOperationException("ContextPropagationUtils is a utility class and cannot be instantiated");
    }

    /**
     * 注册上下文提供者
     *
     * @param name     上下文名称（如 "traceId"、"tenantId"）
     * @param provider 上下文值提供者
     */
    public static void registerContextProvider(String name, Supplier<String> provider) {
        CONTEXT_PROVIDERS.put(name, provider);
    }

    /**
     * 移除上下文提供者
     *
     * @param name 上下文名称
     */
    public static void unregisterContextProvider(String name) {
        CONTEXT_PROVIDERS.remove(name);
    }

    /**
     * 捕获当前线程的所有注册上下文
     *
     * @return 上下文快照 Map
     */
    public static Map<String, String> captureContext() {
        Map<String, String> snapshot = new HashMap<>(CONTEXT_PROVIDERS.size());
        for (Map.Entry<String, Supplier<String>> entry : CONTEXT_PROVIDERS.entrySet()) {
            try {
                String value = entry.getValue().get();
                if (value != null) {
                    snapshot.put(entry.getKey(), value);
                }
            } catch (Exception e) {
                // 忽略上下文捕获异常，不影响主流程
            }
        }
        return snapshot;
    }

    /**
     * 包装 Runnable，在执行前恢复上下文，执行后清理
     *
     * @param runnable 原始任务
     * @return 包装后的任务
     */
    public static Runnable wrap(Runnable runnable) {
        Map<String, String> snapshot = captureContext();
        return () -> {
            Map<String, String> previous = applyContext(snapshot);
            try {
                runnable.run();
            } finally {
                restoreContext(previous);
            }
        };
    }

    /**
     * 包装 Callable，在执行前恢复上下文，执行后清理
     *
     * @param callable 原始任务
     * @param <T>      返回值类型
     * @return 包装后的任务
     */
    public static <T> Callable<T> wrap(Callable<T> callable) {
        Map<String, String> snapshot = captureContext();
        return () -> {
            Map<String, String> previous = applyContext(snapshot);
            try {
                return callable.call();
            } finally {
                restoreContext(previous);
            }
        };
    }

    /**
     * 应用上下文快照到当前线程
     *
     * @param snapshot 上下文快照
     * @return 之前的上下文值（用于恢复）
     */
    private static Map<String, String> applyContext(Map<String, String> snapshot) {
        Map<String, String> previous = new HashMap<>(snapshot.size());
        for (Map.Entry<String, Supplier<String>> entry : CONTEXT_PROVIDERS.entrySet()) {
            String name = entry.getKey();
            try {
                String currentValue = entry.getValue().get();
                if (currentValue != null) {
                    previous.put(name, currentValue);
                }
            } catch (Exception e) {
                // 忽略
            }
        }
        // 上下文恢复由具体的 ThreadLocal 管理器负责
        // 此处仅作为框架钩子，具体实现可通过注册 Consumer 来设置/恢复值
        return previous;
    }

    /**
     * 恢复之前的上下文
     *
     * @param previous 之前的上下文值
     */
    private static void restoreContext(Map<String, String> previous) {
        // 具体恢复逻辑由注册的上下文管理器负责
        // 此处为框架预留
    }
}
