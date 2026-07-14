package com.njydsz.pmis.common.domain.audit;

import java.time.LocalDateTime;

import com.njydsz.pmis.common.core.context.RequestContext;

/**
 * 审计上下文
 *
 * <p>提供统一的审计信息获取入口，从 {@link RequestContext} 提取当前操作人、
 * 租户、追踪 ID 等上下文信息，用于填充实体的审计字段。
 *
 * <p><b>核心能力：</b>
 * <ul>
 *   <li>获取当前操作人ID（优先从 RequestContext 获取）</li>
 *   <li>获取当前租户ID</li>
 *   <li>获取当前链路追踪ID</li>
 *   <li>获取当前时间戳</li>
 *   <li>支持手动设置审计信息（适用于异步任务、定时任务等无请求上下文场景）</li>
 * </ul>
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * // 获取当前操作人
 * String currentUser = AuditContext.currentUser();
 *
 * // 填充实体审计字段
 * entity.setCreatedBy(AuditContext.currentUser());
 * entity.setCreatedAt(AuditContext.now());
 * entity.setUpdatedBy(AuditContext.currentUser());
 * entity.setUpdatedAt(AuditContext.now());
 *
 * // 无请求上下文场景（定时任务等）
 * AuditContext.set("system", "tenant-001");
 * try {
 *     // 业务逻辑...
 * } finally {
 *     AuditContext.clear();
 * }
 * }</pre>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 *
 * @see RequestContext
 */
public final class AuditContext {

    /**
     * 系统默认操作人
     */
    public static final String SYSTEM_USER = "system";

    /**
     * ThreadLocal 手动设置的当前用户
     */
    private static final ThreadLocal<String> MANUAL_USER = new ThreadLocal<>();

    /**
     * ThreadLocal 手动设置的租户ID
     */
    private static final ThreadLocal<String> MANUAL_TENANT = new ThreadLocal<>();

    private AuditContext() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 获取当前操作人ID
     *
     * <p>优先级：手动设置 > RequestContext > 系统默认值
     *
     * @return 当前操作人ID
     */
    public static String currentUser() {
        String manual = MANUAL_USER.get();
        if (manual != null) {
            return manual;
        }
        String fromContext = RequestContext.getUserId();
        return fromContext != null ? fromContext : SYSTEM_USER;
    }

    /**
     * 获取当前租户ID
     *
     * <p>优先级：手动设置 > RequestContext
     *
     * @return 当前租户ID，不存在返回 null
     */
    public static String currentTenant() {
        String manual = MANUAL_TENANT.get();
        if (manual != null) {
            return manual;
        }
        return RequestContext.getTenantId();
    }

    /**
     * 获取当前链路追踪ID
     *
     * @return 当前追踪ID，不存在返回 null
     */
    public static String currentTraceId() {
        return RequestContext.getTraceId();
    }

    /**
     * 获取当前时间
     *
     * @return 当前时间
     */
    public static LocalDateTime now() {
        return LocalDateTime.now();
    }

    /**
     * 手动设置审计信息（适用于无请求上下文的场景）
     *
     * @param userId   操作人ID
     * @param tenantId 租户ID
     */
    public static void set(String userId, String tenantId) {
        MANUAL_USER.set(userId);
        MANUAL_TENANT.set(tenantId);
    }

    /**
     * 设置当前操作人
     *
     * @param userId 操作人ID
     */
    public static void setUser(String userId) {
        MANUAL_USER.set(userId);
    }

    /**
     * 清除手动设置的审计信息
     *
     * <p>在异步任务或定时任务结束时调用，防止 ThreadLocal 泄漏。
     */
    public static void clear() {
        MANUAL_USER.remove();
        MANUAL_TENANT.remove();
    }

    /**
     * 判断当前是否有手动设置的审计信息
     *
     * @return 有手动设置返回 true
     */
    public static boolean hasManualContext() {
        return MANUAL_USER.get() != null || MANUAL_TENANT.get() != null;
    }
}
