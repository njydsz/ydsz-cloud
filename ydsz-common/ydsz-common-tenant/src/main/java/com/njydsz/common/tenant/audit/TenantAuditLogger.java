package com.njydsz.common.tenant.audit;

import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * 租户审计工具类。
 *
 * <p>提供租户维度的审计日志快捷方法，业务代码通过此类
 * 将租户上下文信息附加到审计事件中。
 *
 * <p><b>MDC 约定：</b>tenantId 由 {@code TenantContextWebFilter} 在请求入口设置，
 * 此类仅补充 auditAction 和 resourceId 字段，避免 MDC 重复写入。
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * TenantAuditLogger.log("CREATE_USER", "创建用户: " + username);
 * TenantAuditLogger.log("DELETE_FILE", "删除文件: " + fileId, fileId);
 * }</pre>
 *
 * <p>此为轻量级工具类，实际审计持久化由 {@code common-audit} 模块的
 * {@code @Audit} 注解 + AOP 切面处理。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class TenantAuditLogger {

    private TenantAuditLogger() {
    }

    /**
     * 记录审计日志（携带租户上下文）。
     *
     * <p>MDC tenantId 已由 WebFilter 设置，此处仅补充 auditAction。
     *
     * @param action  操作类型
     * @param message 日志消息
     */
    public static void log(String action, String message) {
        MDC.put("auditAction", action);
        try {
            LoggerFactory.getLogger("TENANT_AUDIT")
                    .info("[{}] {}", action, message);
        } finally {
            MDC.remove("auditAction");
        }
    }

    /**
     * 记录审计日志（携带资源 ID）。
     *
     * @param action     操作类型
     * @param message    日志消息
     * @param resourceId 资源 ID
     */
    public static void log(String action, String message, Object resourceId) {
        MDC.put("auditAction", action);
        MDC.put("resourceId", String.valueOf(resourceId));
        try {
            LoggerFactory.getLogger("TENANT_AUDIT")
                    .info("[{}] {} (resourceId={})", action, message, resourceId);
        } finally {
            MDC.remove("auditAction");
            MDC.remove("resourceId");
        }
    }
}
