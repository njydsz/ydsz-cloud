package com.njydsz.pmis.common.util.concurrent;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

import org.slf4j.MDC;

/**
 * 上下文传播工具类
 *
 * <p>解决线程池/异步场景下 ThreadLocal 上下文丢失问题。
 * 在提交任务到线程池前捕获当前线程的上下文，在任务执行前恢复上下文，执行后清理。
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * // 注册上下文提供者（含 getter 和 setter）
 * ContextPropagationUtils.registerContextProvider(
 *     "traceId",
 *     () -> MDC.get("traceId"),
 *     (name, value) -> { if (value != null) MDC.put(name, value); else MDC.remove(name); }
 * );
 * ContextPropagationUtils.registerContextProvider(
 *     "tenantId",
 *     TenantContext::get,
 *     (name, value) -> TenantContext.set(value)
 * );
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

    private static final Map<String, ContextEntry> CONTEXT_ENTRIES = new ConcurrentHashMap<>();

    private ContextPropagationUtils() {
        throw new UnsupportedOperationException("ContextPropagationUtils is a utility class and cannot be instantiated");
    }

    /** MDC 上下文注册标识 */
    private static final String MDC_CONTEXT_NAME = "__mdc__";

    /** MDC 是否已注册 */
    private static volatile boolean mdcRegistered = false;

    /**
     * 注册内置 MDC 上下文传播
     *
     * <p>将 SLF4J MDC 注册为上下文提供者，使 MDC 中的所键值对自动跨线程传播。
     * 此方法使用 MDC 的 getCopyOfContextMap / setContextMap 实现整体快照传播，
     * 无需为每个 MDC key 单独注册。
     *
     * <p>调用此方法后，通过 {@link #wrap(Runnable)} 或 {@link #wrap(Callable)} 提交的任务
     * 将自动继承调用线程的 MDC 上下文。
     *
     * <pre>{@code
     * // 在应用启动时注册
     * ContextPropagationUtils.registerMdcContext();
     *
     * // 提交任务到线程池时自动传播 MDC
     * executor.submit(ContextPropagationUtils.wrap(() -> {
     *     // 此处可正常访问 MDC.get("traceId") 等
     *     log.info("Processing in async thread");
     * }));
     * }</pre>
     *
     * @return true 表示首次注册成功，false 表示已注册过
     */
    public static synchronized boolean registerMdcContext() {
        if (mdcRegistered) {
            return false;
        }
        CONTEXT_ENTRIES.put(MDC_CONTEXT_NAME, new ContextEntry(
            () -> {
                Map<String, String> mdcMap = MDC.getCopyOfContextMap();
                if (mdcMap == null || mdcMap.isEmpty()) {
                    return null;
                }
                // 将 MDC map 序列化为 "key1=value1\u0001key2=value2" 格式
                StringBuilder sb = new StringBuilder();
                for (Map.Entry<String, String> entry : mdcMap.entrySet()) {
                    if (sb.length() > 0) {
                        sb.append('\u0001');
                    }
                    sb.append(entry.getKey()).append('=').append(entry.getValue());
                }
                return sb.toString();
            },
            (name, value) -> {
                if (value == null) {
                    MDC.clear();
                } else {
                    Map<String, String> mdcMap = new HashMap<>();
                    String[] pairs = value.split("\u0001");
                    for (String pair : pairs) {
                        int idx = pair.indexOf('=');
                        if (idx > 0) {
                            mdcMap.put(pair.substring(0, idx), pair.substring(idx + 1));
                        }
                    }
                    MDC.setContextMap(mdcMap);
                }
            }
        ));
        mdcRegistered = true;
        return true;
    }

    /**
     * 注销内置 MDC 上下文传播
     */
    public static synchronized void unregisterMdcContext() {
        CONTEXT_ENTRIES.remove(MDC_CONTEXT_NAME);
        mdcRegistered = false;
    }

    /**
     * 注册上下文提供者（含 getter 和 setter）
     *
     * <p>setter 负责将值写入当前线程的 ThreadLocal（或等效存储）。
     * 当 value 为 null 时，setter 应清除该上下文项。
     *
     * @param name     上下文名称（如 "traceId"、"tenantId"）
     * @param getter   上下文值读取器（从当前线程读取值）
     * @param setter   上下文值写入器（将值写入当前线程），value=null 表示清除
     */
    public static void registerContextProvider(String name, Supplier<String> getter, BiConsumer<String, String> setter) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("name must not be null or empty");
        }
        if (getter == null || setter == null) {
            throw new IllegalArgumentException("getter and setter must not be null");
        }
        CONTEXT_ENTRIES.put(name, new ContextEntry(getter, setter));
    }

    /**
     * 注册上下文提供者（仅 getter，setter 为空操作）
     *
     * <p>此重载仅用于只读场景，不实际传播上下文值。
     * 推荐使用 {@link #registerContextProvider(String, Supplier, BiConsumer)} 注册完整条目。
     *
     * @param name     上下文名称
     * @param getter   上下文值读取器
     * @deprecated 使用 {@link #registerContextProvider(String, Supplier, BiConsumer)} 替代
     */
    @Deprecated(since = "1.3.0", forRemoval = true)
    public static void registerContextProvider(String name, Supplier<String> getter) {
        registerContextProvider(name, getter, (n, v) -> { });
    }

    /**
     * 移除上下文提供者
     *
     * @param name 上下文名称
     */
    public static void unregisterContextProvider(String name) {
        CONTEXT_ENTRIES.remove(name);
    }

    /**
     * 捕获当前线程的所有注册上下文
     *
     * @return 上下文快照 Map
     */
    public static Map<String, String> captureContext() {
        Map<String, String> snapshot = new HashMap<>(CONTEXT_ENTRIES.size());
        for (Map.Entry<String, ContextEntry> entry : CONTEXT_ENTRIES.entrySet()) {
            try {
                String value = entry.getValue().getter.get();
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
     * <p>将快照中的值通过 setter 写入当前线程的 ThreadLocal，
     * 同时保存当前线程的原有值用于后续恢复。
     *
     * @param snapshot 上下文快照
     * @return 之前的上下文值（用于恢复，可能包含 null 值表示原本不存在）
     */
    private static Map<String, String> applyContext(Map<String, String> snapshot) {
        Map<String, String> previous = new HashMap<>(CONTEXT_ENTRIES.size());
        for (Map.Entry<String, ContextEntry> entry : CONTEXT_ENTRIES.entrySet()) {
            String name = entry.getKey();
            ContextEntry ctxEntry = entry.getValue();
            try {
                // 保存当前线程的原有值
                String currentValue = ctxEntry.getter.get();
                previous.put(name, currentValue);
                // 从快照中恢复值
                String snapshotValue = snapshot.get(name);
                if (snapshotValue != null) {
                    ctxEntry.setter.accept(name, snapshotValue);
                } else {
                    // 快照中不存在该上下文，清除当前线程的值
                    ctxEntry.setter.accept(name, null);
                }
            } catch (Exception e) {
                // 忽略单个上下文设置异常，不影响其他上下文
            }
        }
        return previous;
    }

    /**
     * 恢复之前的上下文
     *
     * <p>将之前保存的值通过 setter 写回当前线程。
     * 若之前值为 null，则清除该上下文项。
     *
     * @param previous 之前的上下文值
     */
    private static void restoreContext(Map<String, String> previous) {
        for (Map.Entry<String, String> entry : previous.entrySet()) {
            String name = entry.getKey();
            String value = entry.getValue();
            ContextEntry ctxEntry = CONTEXT_ENTRIES.get(name);
            if (ctxEntry != null) {
                try {
                    if (value != null) {
                        ctxEntry.setter.accept(name, value);
                    } else {
                        ctxEntry.setter.accept(name, null);
                    }
                } catch (Exception e) {
                    // 忽略恢复异常
                }
            }
        }
    }

    /**
     * 上下文条目（getter + setter 对）
     */
    private static final class ContextEntry {
        final Supplier<String> getter;
        final BiConsumer<String, String> setter;

        ContextEntry(Supplier<String> getter, BiConsumer<String, String> setter) {
            this.getter = getter;
            this.setter = setter;
        }
    }
}
