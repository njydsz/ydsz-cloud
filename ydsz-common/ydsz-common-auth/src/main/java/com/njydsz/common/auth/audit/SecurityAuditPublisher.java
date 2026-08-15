package com.njydsz.common.auth.audit;

/**
 * 安全审计事件发布者。
 *
 * <p>定义审计事件的发布契约，实现类负责将事件输出到
 * 日志系统、消息队列或外部审计平台。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface SecurityAuditPublisher {

    /**
     * 发布审计事件。
     *
     * @param event 审计事件，不可为 {@code null}
     */
    void publish(SecurityAuditEvent event);
}
