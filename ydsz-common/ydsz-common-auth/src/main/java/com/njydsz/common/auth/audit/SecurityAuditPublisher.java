package com.njydsz.common.auth.audit;

/**
 * 安全审计事件发布者。
 *
 * <p>定义审计事件的发布契约，实现类负责将事件输出到
 * 日志系统、消息队列或外部审计平台。
 *
 * <p><b>迁移指南：</b>安全审计与认证鉴权属于不同关注点，已超出本模块职责边界。
 * 请迁移至独立的安全审计模块或对接公司统一审计平台。</p>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @deprecated 自 3.0.0 起标记废弃，计划 4.0.0 移除。
 *             迁移目标：独立安全审计模块或公司统一审计平台。
 */
@Deprecated(forRemoval = true, since = "3.0.0")
public interface SecurityAuditPublisher {

    /**
     * 发布审计事件。
     *
     * @param event 审计事件，不可为 {@code null}
     */
    void publish(SecurityAuditEvent event);
}
