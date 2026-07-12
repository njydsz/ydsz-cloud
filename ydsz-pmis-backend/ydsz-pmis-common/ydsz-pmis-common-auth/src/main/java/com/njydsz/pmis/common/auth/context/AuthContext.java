package com.njydsz.pmis.common.auth.context;

import com.alibaba.ttl.TransmittableThreadLocal;
import com.njydsz.pmis.common.auth.model.ColumnPermissionInfo;

/**
 * 统一权限上下文持有者
 *
 * <p>合并原 PermissionContextHolder 和 ColumnPermissionContext，提供统一的线程级权限上下文管理。
 * 使用 TransmittableThreadLocal 保证在线程池场景下的正确传递。
 *
 * <p><b>存储内容：</b>
 * <ul>
 *   <li>tenantId: 租户ID，用于多租户场景下的数据隔离</li>
 *   <li>columnPermission: 列权限信息，用于字段级权限控制</li>
 * </ul>
 *
 * <p><b>生命周期：</b>
 * <ul>
 *   <li>在 Filter/Interceptor 中初始化</li>
 *   <li>在业务逻辑中读取</li>
 *   <li>在请求结束时必须调用 {@link #clear()} 清理，防止内存泄漏</li>
 * </ul>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
public final class AuthContext {

    private static final TransmittableThreadLocal<ContextData> CONTEXT = new TransmittableThreadLocal<>();

    private AuthContext() {
    }

    /**
     * 获取当前线程的上下文数据
     *
     * @return 上下文数据，未设置时返回 null
     */
    public static ContextData get() {
        return CONTEXT.get();
    }

    /**
     * 设置当前线程的上下文数据
     *
     * @param data 上下文数据
     */
    public static void set(ContextData data) {
        CONTEXT.set(data);
    }

    /**
     * 获取租户ID
     *
     * @return 租户ID，未设置时返回 null
     */
    public static String getTenantId() {
        ContextData data = CONTEXT.get();
        return data != null ? data.getTenantId() : null;
    }

    /**
     * 设置租户ID
     *
     * @param tenantId 租户ID
     */
    public static void setTenantId(String tenantId) {
        ContextData data = CONTEXT.get();
        if (data == null) {
            data = new ContextData();
            CONTEXT.set(data);
        }
        data.setTenantId(tenantId);
    }

    /**
     * 获取列权限信息
     *
     * @return 列权限信息，未设置时返回 null
     */
    public static ColumnPermissionInfo getColumnPermission() {
        ContextData data = CONTEXT.get();
        return data != null ? data.getColumnPermission() : null;
    }

    /**
     * 设置列权限信息
     *
     * @param columnPermission 列权限信息
     */
    public static void setColumnPermission(ColumnPermissionInfo columnPermission) {
        ContextData data = CONTEXT.get();
        if (data == null) {
            data = new ContextData();
            CONTEXT.set(data);
        }
        data.setColumnPermission(columnPermission);
    }

    /**
     * 判断是否有列权限
     *
     * @return true 表示有列权限且权限信息非空
     */
    public static boolean hasColumnPermission() {
        ColumnPermissionInfo info = getColumnPermission();
        return info != null && !info.isEmpty();
    }

    /**
     * 清理当前线程的上下文数据
     *
     * <p>必须在请求结束时调用（通常由 Filter 或 Interceptor 负责），
     * 防止 ThreadLocal 内存泄漏。
     */
    public static void clear() {
        CONTEXT.remove();
    }

    /**
     * 上下文数据载体
     */
    public static class ContextData {
        private String tenantId;
        private ColumnPermissionInfo columnPermission;

        public String getTenantId() {
            return tenantId;
        }

        public void setTenantId(String tenantId) {
            this.tenantId = tenantId;
        }

        public ColumnPermissionInfo getColumnPermission() {
            return columnPermission;
        }

        public void setColumnPermission(ColumnPermissionInfo columnPermission) {
            this.columnPermission = columnPermission;
        }
    }
}
