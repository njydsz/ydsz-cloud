package com.remisoft.common.core.context;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.alibaba.ttl.TransmittableThreadLocal;

/**
 * 跨服务传播的元数据持有者。
 *
 * <p>专用于存储需要在服务间透传的用户自定义键值对（如 {@code appId}、{@code businessLine} 等）。
 * 与 {@link RequestContext} 中的内置上下文键（userId、tenantId 等）分离，语义更清晰，
 * 且可根据需要独立控制是否参与传播。</p>
 *
 * <p>使用独立的 TTL 持有者，确保清理主上下文时 metadata 也同步清理
 * （通过 {@link RequestContext#clear()} 的回调自动同步）。</p>
 *
 * <p><b>使用示例（HTTP 客户端拦截器）：</b></p>
 * <pre>{@code
 * // 导出当前元数据并注入下游请求
 * exportMetadata().forEach((key, value) -> request.setHeader("X-Metadata-" + key, value));
 *
 * // 从上游请求导入元数据
 * Map<String, String> imported = new HashMap<>();
 * Collections.list(request.getHeaderNames()).stream()
 *     .filter(h -> h.startsWith("X-Metadata-"))
 *     .forEach(h -> imported.put(h.substring("X-Metadata-".length()), request.getHeader(h)));
 * importMetadata(imported);
 * }</pre>
 *
 * @author remi-team
 * @since 2.1.0
 * @see RequestContext
 */
public final class RequestMetadata {

    /**
     * 跨服务传播的元数据存储（懒初始化）。
     *
     * <p>使用独立的 TTL 持有者，确保清理主上下文时 metadata 也同步清理。</p>
     */
    private static final ThreadLocal<Map<String, String>> METADATA_HOLDER =
            new TransmittableThreadLocal<Map<String, String>>() {
                @Override
                protected Map<String, String> initialValue() {
                    return null; // 懒初始化
                }
            };

    private RequestMetadata() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 设置元数据属性
     *
     * <p>用于在请求处理链中传递自定义键值对，会跨服务自动传播。</p>
     *
     * @param key   属性键（不可为 null）
     * @param value 属性值
     * @since 2.1.0
     */
    public static void put(String key, String value) {
        if (key == null) {
            throw new IllegalArgumentException("Metadata key must not be null");
        }
        Map<String, String> holder = METADATA_HOLDER.get();
        if (holder == null) {
            holder = new HashMap<>(4);
            METADATA_HOLDER.set(holder);
        }
        if (value != null) {
            holder.put(key, value);
        } else {
            holder.remove(key);
        }
    }

    /**
     * 获取元数据属性
     *
     * @param key 属性键
     * @return 属性值；不存在时返回 null
     * @since 2.1.0
     */
    public static String get(String key) {
        Map<String, String> holder = METADATA_HOLDER.get();
        return holder != null ? holder.get(key) : null;
    }

    /**
     * 导出当前线程的所有元数据（不可变快照）
     *
     * <p>常用于将当前上下文中的元数据透传到下游服务（通过 HTTP 请求头）。
     * 返回的 Map 不可变，修改尝试将抛出 UnsupportedOperationException。</p>
     *
     * @return 元数据的不可变快照（空 Map 而非 null）
     * @since 2.1.0
     */
    public static Map<String, String> export() {
        Map<String, String> holder = METADATA_HOLDER.get();
        if (holder == null || holder.isEmpty()) {
            return Collections.emptyMap();
        }
        return Collections.unmodifiableMap(new HashMap<>(holder));
    }

    /**
     * 从外部（通常是上游服务的 HTTP 请求头）导入元数据到当前线程上下文
     *
     * <p>建议在入口过滤器中调用，用于接收上游传递的元数据并设置到当前上下文。</p>
     *
     * @param metadata 要导入的元数据 Map（可为 null 或空）
     * @since 2.1.0
     */
    public static void importMetadata(Map<String, String> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return;
        }
        Map<String, String> holder = METADATA_HOLDER.get();
        if (holder == null) {
            holder = new HashMap<>(metadata.size());
            METADATA_HOLDER.set(holder);
        }
        holder.putAll(metadata);
    }

    /**
     * 清空当前线程的元数据。
     *
     * <p>由 {@link RequestContext#clear()} 自动调用，无需手动调用。</p>
     *
     * @since 2.1.0
     */
    static void clear() {
        METADATA_HOLDER.remove();
    }
}
