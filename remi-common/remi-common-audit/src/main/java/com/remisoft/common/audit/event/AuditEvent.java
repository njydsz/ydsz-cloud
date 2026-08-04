package com.remisoft.common.audit.event;

import org.springframework.context.ApplicationEvent;

import com.remisoft.common.audit.domain.AuditLog;

/**
 * 审计事件
 * <p>
 * 基于 Spring ApplicationEvent 实现的审计日志事件，用于解耦审计日志的产生与存储。
 * 切面发布事件，监听器异步消费并写入存储。
 * </p>
 *
 * <p><b>线程模型：</b>事件监听默认在发布者所在线程同步执行；
 * 如需异步落盘，可通过 {@code @Async("auditAsyncExecutor")} 或
 * 由 {@link com.remisoft.common.audit.core.AsyncAuditRecorder} 内部异步化处理。</p>
 *
 * @author remi-team
 * @since 1.0.0
 */
public class AuditEvent extends ApplicationEvent {

    private static final long serialVersionUID = 1L;

    /**
     * 审计日志实体（不可变，避免监听器修改切面已构建的快照）
     */
    private final AuditLog auditLog;

    /**
     * 用户令牌（用于跨链路身份透传；脱敏展示）
     */
    private final String token;

    /**
     * 构造审计事件
     *
     * @param source   事件源（通常是切面实例）
     * @param auditLog 审计日志实体
     * @param token    用户令牌（可为 null）
     */
    public AuditEvent(Object source, AuditLog auditLog, String token) {
        super(source);
        this.auditLog = auditLog;
        this.token = token;
    }

    /**
     * 创建无令牌的审计事件
     *
     * @param source   事件源
     * @param auditLog 审计日志
     * @return 审计事件
     */
    public static AuditEvent of(Object source, AuditLog auditLog) {
        return new AuditEvent(source, auditLog, null);
    }

    /**
     * 创建带令牌的审计事件
     *
     * @param source   事件源
     * @param auditLog 审计日志
     * @param token    用户令牌
     * @return 审计事件
     */
    public static AuditEvent of(Object source, AuditLog auditLog, String token) {
        return new AuditEvent(source, auditLog, token);
    }

    /**
     * 获取审计日志实体
     *
     * @return 审计日志实体
     */
    public AuditLog getAuditLog() {
        return auditLog;
    }

    /**
     * 获取用户令牌
     *
     * @return 用户令牌
     */
    public String getToken() {
        return token;
    }

    @Override
    public String toString() {
        return "AuditEvent{" +
                "auditLogId='" + (auditLog != null ? auditLog.getId() : null) + '\'' +
                ", token='" + (token != null ? "****" : null) + '\'' +
                '}';
    }
}
