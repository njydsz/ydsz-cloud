package com.njydsz.pmis.common.audit.context;

import com.njydsz.pmis.common.core.context.RequestContext;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 审计上下文（基于 ThreadLocal）
 * <p>
 * 管理审计日志的上下文信息（IP、URL、Token、BusinessNo 等），确保线程隔离。
 * 采用 {@link InheritableThreadLocal} 便于子线程继承父线程的审计上下文。
 * </p>
 *
 * <p><b>线程安全：</b>静态方法+ThreadLocal 实现，无共享状态。<br>
 * <b>内存泄漏防护：</b>必须在请求结束（{@link #clear()}）时清理 ThreadLocal，
 * 切面已在 finally 块统一清理，业务方无需手动调用。</p>
 *
 * <p>通用字段（如 operatorId/operatorName）已从 {@link RequestContext} 获取，
 * 避免重复存储，保持数据一致性。</p>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 1.0.0
 */
@SuppressWarnings("unchecked")
public class AuditContext {

    /**
     * 当前线程的审计上下文（InheritableThreadLocal 支持子线程继承）
     */
    private static final InheritableThreadLocal<AuditContextData> CONTEXT_THREAD_LOCAL = new InheritableThreadLocal<>();

    /**
     * 将 Runnable 包装为携带当前审计上下文的任务
     * <p>使异步线程能够继承调用线程的审计上下文信息。
     * 执行完毕后会自动还原原线程的上下文，避免污染线程池中的其他任务。
     *
     * @param action 需要执行的任务
     * @return 包装了审计上下文传递逻辑的 Runnable
     */
    public static Runnable wrap(Runnable action) {
        AuditContextData snapshot = get();
        return () -> {
            AuditContextData previous = get();
            try {
                if (snapshot != null) {
                    set(snapshot);
                }
                action.run();
            } finally {
                if (previous != null) {
                    set(previous);
                } else {
                    clear();
                }
            }
        };
    }

    /**
     * 设置审计上下文
     *
     * @param data 审计上下文数据
     */
    public static void set(AuditContextData data) {
        CONTEXT_THREAD_LOCAL.remove();
        CONTEXT_THREAD_LOCAL.set(data);
    }

    /**
     * 获取当前线程的审计上下文
     *
     * @return 审计上下文数据；不存在时返回 null
     */
    public static AuditContextData get() {
        return CONTEXT_THREAD_LOCAL.get();
    }

    /**
     * 清除当前线程的审计上下文
     * <p>防止内存泄漏，务必在请求结束或异步任务结束时调用。
     */
    public static void clear() {
        CONTEXT_THREAD_LOCAL.remove();
    }

    /**
     * 在指定审计上下文中执行操作，执行完毕后自动清理上下文
     *
     * @param data   审计上下文数据
     * @param action 要执行的操作
     */
    public static void runWithContext(AuditContextData data, Runnable action) {
        try {
            set(data);
            action.run();
        } finally {
            clear();
        }
    }

    /**
     * 审计上下文数据载体
     * <p>线程局部存储的审计上下文数据对象。通用字段（如 operatorId/operatorName）
     * 已从 {@link RequestContext} 获取，避免重复存储。
     */
    public static class AuditContextData {

        /**
         * 请求开始时间（毫秒时间戳）
         */
        private Long startTime;

        /**
         * 请求完整 URL（含 scheme/host/port）
         */
        private String url;

        /**
         * 请求 URI（不含 queryString）
         */
        private String uri;

        /**
         * HTTP 方法（GET/POST 等）
         */
        private String httpMethod;

        /**
         * 客户端 IP 地址
         */
        private String ipAddress;

        /**
         * 用户令牌（透传）
         */
        private String token;

        /**
         * 业务流水号（透传）
         */
        private String businessNo;

        /**
         * 请求参数数组（用于切面序列化）
         */
        private Object[] requestArgs;

        /**
         * 扩展信息（traceId/userAgent/ipLocation 等）
         */
        private Map<String, Object> extra = new ConcurrentHashMap<>();

        /**
         * 获取请求耗时（毫秒）
         *
         * @return 请求耗时；startTime 为 null 时返回 0
         */
        public long getCostTime() {
            if (startTime == null) {
                return 0;
            }
            return System.currentTimeMillis() - startTime;
        }

        /**
         * 获取操作人 ID（从 {@link RequestContext} 透传）
         *
         * @return 操作人 ID
         */
        public String getOperatorId() {
            return RequestContext.getUserId();
        }

        /**
         * 获取操作人姓名（从 {@link RequestContext} 透传）
         *
         * @return 操作人姓名
         */
        public String getOperatorName() {
            return (String) RequestContext.get("userName");
        }

        public Long getStartTime() {
            return startTime;
        }

        public void setStartTime(Long startTime) {
            this.startTime = startTime;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getUri() {
            return uri;
        }

        public void setUri(String uri) {
            this.uri = uri;
        }

        public String getHttpMethod() {
            return httpMethod;
        }

        public void setHttpMethod(String httpMethod) {
            this.httpMethod = httpMethod;
        }

        public String getIpAddress() {
            return ipAddress;
        }

        public void setIpAddress(String ipAddress) {
            this.ipAddress = ipAddress;
        }

        public String getToken() {
            return token;
        }

        public void setToken(String token) {
            this.token = token;
        }

        public String getBusinessNo() {
            return businessNo;
        }

        public void setBusinessNo(String businessNo) {
            this.businessNo = businessNo;
        }

        public Object[] getRequestArgs() {
            return requestArgs;
        }

        public void setRequestArgs(Object[] requestArgs) {
            this.requestArgs = requestArgs;
        }

        public Map<String, Object> getExtra() {
            return extra;
        }

        public void setExtra(Map<String, Object> extra) {
            this.extra = extra;
        }

        /**
         * 添加扩展信息
         *
         * @param key 键
         * @param value 值
         */
        public void putExtra(String key, Object value) {
            this.extra.put(key, value);
        }

        /**
         * 获取扩展信息
         *
         * @param key 键
         * @param <T> 值类型
         * @return 值
         */
        
        public <T> T getExtra(String key) {
            return (T) this.extra.get(key);
        }
    }
}
