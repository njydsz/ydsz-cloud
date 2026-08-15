package com.njydsz.common.auth.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 默认安全审计事件发布者实现。
 *
 * <p>使用独立的 SLF4J AUDIT 日志记录器将事件格式化为结构化日志输出。
 * 日志记录器名称为 {@code SECURITY_AUDIT}，可在日志框架配置中单独路由到文件
 * 或外部收集系统。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class DefaultSecurityAuditPublisher implements SecurityAuditPublisher {

    private static final Logger auditLog = LoggerFactory.getLogger("SECURITY_AUDIT");

    @Override
    public void publish(SecurityAuditEvent event) {
        if (event == null) {
            return;
        }

        auditLog.warn(event.toJson());
    }
}
