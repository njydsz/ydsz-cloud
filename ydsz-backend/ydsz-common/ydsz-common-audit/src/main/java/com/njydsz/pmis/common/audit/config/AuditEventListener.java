package com.njydsz.common.audit.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;

import com.njydsz.common.audit.core.AuditRecorder;
import com.njydsz.common.audit.domain.AuditLog;
import com.njydsz.common.audit.event.AuditEvent;

/**
 * 审计事件监听器
 *
 * <p>接收审计事件并委托给 {@link AuditRecorder} 进行异步批量保存。
 * 采用同步监听 + 委托异步记录器的方式，既保留了发布/监听解耦模式，
 * 又由功能更完善的 {@link AuditRecorder} 统一处理异步批量写入、
 * 队列满兜底、磁盘降级、优雅停机等机制。
 *
 * @author ydsz-team
 * @since 1.0.0
 * 
 */
public class AuditEventListener {

    private static final Logger log = LoggerFactory.getLogger(AuditEventListener.class);

    /** 审计记录器，用于将审计日志写入存储 */
    private final AuditRecorder auditRecorder;

    /**
     * 构造审计事件监听器
     *
     * @param auditRecorder 审计记录器
     */
    public AuditEventListener(AuditRecorder auditRecorder) {
        this.auditRecorder = auditRecorder;
    }

    /**
     * 处理审计事件（同步监听，委托给异步记录器）
     *
     * @param event 审计事件
     */
    @EventListener
    public void onAuditEvent(AuditEvent event) {
        if (event == null || event.getAuditLog() == null) {
            log.warn("【审计监听】审计事件或审计日志为空,跳过处理");
            return;
        }

        try {
            AuditLog auditLog = event.getAuditLog();
            auditRecorder.record(auditLog);
        } catch (Exception e) {
            log.error("【审计监听】处理审计事件失败: {}", e.getMessage(), e);
        }
    }
}
