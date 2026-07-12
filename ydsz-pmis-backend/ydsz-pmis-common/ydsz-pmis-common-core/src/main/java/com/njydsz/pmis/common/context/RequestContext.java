package com.njydsz.pmis.common.context;

import com.alibaba.ttl.TransmittableThreadLocal;
import lombok.Builder;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * 请求上下文（基于 TransmittableThreadLocal）
 *
 * <p>使用 TTL 替代普通 ThreadLocal，解决以下问题：
 * <ul>
 *   <li>{@code @Async} 异步方法中上下文丢失</li>
 *   <li>线程池复用时上下文不传递</li>
 *   <li>CompletableFuture 链式调用中上下文断裂</li>
 * </ul>
 *
 * <h3>核心字段</h3>
 * <ul>
 *   <li>{@code traceId} - 链路追踪 ID（与 MDC traceId 同步）</li>
 *   <li>{@code tenantId} - 租户 ID</li>
 *   <li>{@code userId} - 当前用户 ID</li>
 *   <li>{@code username} - 当前用户名</li>
 *   <li>{@code clientIp} - 客户端 IP</li>
 *   <li>{@code requestPath} - 请求路径</li>
 *   <li>{@code httpMethod} - HTTP 方法</li>
 *   <li>{@code extras} - 扩展属性（业务自定义）</li>
 * </ul>
 *
 * <h3>使用方式</h3>
 * <pre>{@code
 * // 在拦截器/过滤器中设置
 * RequestContext ctx = RequestContext.builder()
 *     .traceId(traceId)
 *     .tenantId(tenantId)
 *     .userId(userId)
 *     .build();
 * RequestContext.set(ctx);
 *
 * // 在业务层/异步线程中获取
 * String traceId = RequestContext.getTraceId();
 * String tenantId = RequestContext.getTenantId();
 *
 * // 请求结束时清理
 * RequestContext.clear();
 * }</pre>
 *
 * <p><b>注意</b>：必须在请求结束时调用 {@link #clear()}，防止线程池复用导致上下文泄漏。
 * 推荐使用 try-finally 或 {@code CleanupGuard} 模式。
 *
 * @author ydsz-pmis-team
 * @since 1.5.0
 */
public final class RequestContext {

    /** TTL 上下文持有器 */
    private static final TransmittableThreadLocal<ContextData> HOLDER =
            new TransmittableThreadLocal<>();

    private RequestContext() {
    }

    /**
     * 设置请求上下文
     *
     * @param ctx 上下文数据
     */
    public static void set(ContextData ctx) {
        HOLDER.set(ctx);
    }

    /**
     * 获取请求上下文
     *
     * @return 上下文数据；未设置时返回 null
     */
    public static ContextData get() {
        return HOLDER.get();
    }

    /**
     * 获取请求上下文（不存在时返回默认空上下文）
     *
     * @return 上下文数据
     */
    public static ContextData getOrDefault() {
        ContextData ctx = HOLDER.get();
        if (ctx == null) {
            ctx = ContextData.builder().build();
            HOLDER.set(ctx);
        }
        return ctx;
    }

    /**
     * 清除上下文（防止线程池复用泄漏）
     */
    public static void clear() {
        HOLDER.remove();
    }

    // ==================== 便捷方法 ====================

    /**
     * 获取当前 traceId
     *
     * @return traceId；未设置时返回 null
     */
    public static String getTraceId() {
        ContextData ctx = HOLDER.get();
        return ctx != null ? ctx.getTraceId() : null;
    }

    /**
     * 设置 traceId
     *
     * @param traceId 链路追踪 ID
     */
    public static void setTraceId(String traceId) {
        getOrDefault().setTraceId(traceId);
    }

    /**
     * 获取当前租户 ID
     *
     * @return 租户 ID；未设置时返回 null
     */
    public static String getTenantId() {
        ContextData ctx = HOLDER.get();
        return ctx != null ? ctx.getTenantId() : null;
    }

    /**
     * 设置租户 ID
     *
     * @param tenantId 租户 ID
     */
    public static void setTenantId(String tenantId) {
        getOrDefault().setTenantId(tenantId);
    }

    /**
     * 获取当前用户 ID
     *
     * @return 用户 ID；未设置时返回 null
     */
    public static String getUserId() {
        ContextData ctx = HOLDER.get();
        return ctx != null ? ctx.getUserId() : null;
    }

    /**
     * 设置用户 ID
     *
     * @param userId 用户 ID
     */
    public static void setUserId(String userId) {
        getOrDefault().setUserId(userId);
    }

    /**
     * 获取当前用户名
     *
     * @return 用户名；未设置时返回 null
     */
    public static String getUsername() {
        ContextData ctx = HOLDER.get();
        return ctx != null ? ctx.getUsername() : null;
    }

    /**
     * 设置用户名
     *
     * @param username 用户名
     */
    public static void setUsername(String username) {
        getOrDefault().setUsername(username);
    }

    /**
     * 获取客户端 IP
     *
     * @return 客户端 IP；未设置时返回 null
     */
    public static String getClientIp() {
        ContextData ctx = HOLDER.get();
        return ctx != null ? ctx.getClientIp() : null;
    }

    /**
     * 设置客户端 IP
     *
     * @param clientIp 客户端 IP
     */
    public static void setClientIp(String clientIp) {
        getOrDefault().setClientIp(clientIp);
    }

    /**
     * 获取扩展属性
     *
     * @param key 属性键
     * @return 属性值；不存在时返回 null
     */
    @SuppressWarnings("unchecked")
    public static <T> T getAttribute(String key) {
        ContextData ctx = HOLDER.get();
        if (ctx == null || ctx.getExtras() == null) {
            return null;
        }
        return (T) ctx.getExtras().get(key);
    }

    /**
     * 设置扩展属性
     *
     * @param key   属性键
     * @param value 属性值
     */
    public static void setAttribute(String key, Object value) {
        getOrDefault().addExtra(key, value);
    }

    /**
     * 上下文数据
     */
    @Data
    @Builder
    public static class ContextData implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /** 链路追踪 ID */
        private String traceId;

        /** 租户 ID */
        private String tenantId;

        /** 用户 ID */
        private String userId;

        /** 用户名 */
        private String username;

        /** 真实姓名 */
        private String realName;

        /** 部门 ID */
        private String deptId;

        /** 客户端 IP */
        private String clientIp;

        /** 请求路径 */
        private String requestPath;

        /** HTTP 方法 */
        private String httpMethod;

        /** 请求时间戳（毫秒） */
        private Long requestTimestamp;

        /** 扩展属性 */
        @Builder.Default
        private Map<String, Object> extras = new HashMap<>();

        /**
         * 添加扩展属性
         *
         * @param key   键
         * @param value 值
         */
        public void addExtra(String key, Object value) {
            if (extras == null) {
                extras = new HashMap<>();
            }
            extras.put(key, value);
        }

        /**
         * 获取扩展属性
         *
         * @param key 键
         * @return 值
         */
        @SuppressWarnings("unchecked")
        public <T> T getExtra(String key) {
            if (extras == null) {
                return null;
            }
            return (T) extras.get(key);
        }
    }
}
