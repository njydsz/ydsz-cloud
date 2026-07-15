package com.njydsz.pmis.common.auth.context;

import com.alibaba.ttl.TransmittableThreadLocal;
import com.njydsz.pmis.common.auth.model.ColumnPermissionInfo;

/**
 * 列权限上下文持有者。
 *
 * <p>使用 ThreadLocal 存储当前线程的列权限信息，
 * 用于在同一个请求链路中传递列权限数据。
 *
 * <p><b>生命周期：</b>
 * <ul>
 *   <li>在 {@link com.njydsz.pmis.common.auth.aspect.AuthColPermissionAspect} 切面中写入</li>
 *   <li>在 SQL 拦截器或 Feign 拦截器中读取</li>
 *   <li>在 finally 块中清理，防止内存泄漏</li>
 * </ul>
 *
 * @since 1.0.0
 * 
 * @see ColumnPermissionInfo
 */
public class ColumnPermissionContext {

    private static final TransmittableThreadLocal<ColumnPermissionInfo> CONTEXT = new TransmittableThreadLocal<>();

    private ColumnPermissionContext() {
    }

    public static void set(ColumnPermissionInfo info) {
        CONTEXT.set(info);
    }

    public static ColumnPermissionInfo get() {
        return CONTEXT.get();
    }

    public static void remove() {
        CONTEXT.remove();
    }

    public static boolean hasPermission() {
        ColumnPermissionInfo info = CONTEXT.get();
        return info != null && !info.isEmpty();
    }
}
