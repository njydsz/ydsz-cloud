package com.remisoft.common.audit.event;

import org.springframework.context.ApplicationEvent;

import lombok.Builder;
import lombok.Getter;

/**
 * 操作日志事件
 *
 * <p>由操作日志切面（或业务代码）在需要记录用户操作行为时发布，
 * 由 {@code OperationLogListener} 异步消费并落库到 {@code remi_operation_log}。
 *
 * <p>与 {@link AuditEvent} 的区别：
 * <ul>
 *   <li>{@link AuditEvent} 携带 {@code AuditLog} 实体，面向通用审计链路</li>
 *   <li>{@code OperationLogEvent} 面向操作日志表，字段与 {@code remi_operation_log} 对齐，
 *       额外包含 beforeData / afterData 变更差异</li>
 * </ul>
 *
 * @author remi-team
 * @since 1.0.0
 */
@Getter
public class OperationLogEvent extends ApplicationEvent {

    private static final long serialVersionUID = 1L;

    /** 模块名 */
    private final String module;
    /** 操作名 */
    private final String action;
    /** 业务类型 */
    private final String bizType;
    /** 业务单据 ID */
    private final String bizId;
    /** 用户 ID */
    private final String userId;
    /** 用户名 */
    private final String username;
    /** 请求 URL */
    private final String requestUrl;
    /** HTTP Method */
    private final String httpMethod;
    /** 方法签名 */
    private final String methodSignature;
    /** 客户端 IP */
    private final String clientIp;
    /** User-Agent */
    private final String userAgent;
    /** 入参 JSON */
    private final String paramsJson;
    /** 响应 JSON */
    private final String responseJson;
    /** 变更前数据（JSON） */
    private final String beforeData;
    /** 变更后数据（JSON） */
    private final String afterData;
    /** 状态: SUCCESS / FAILED */
    private final String status;
    /** 错误信息 */
    private final String errorMessage;
    /** 耗时(毫秒) */
    private final Long costMs;
    /** 链路追踪 ID */
    private final String traceId;
    /** 租户 ID */
    private final String tenantId;

    @Builder
    public OperationLogEvent(Object source, String module, String action, String bizType, String bizId,
                             String userId, String username, String requestUrl, String httpMethod,
                             String methodSignature, String clientIp, String userAgent,
                             String paramsJson, String responseJson, String beforeData, String afterData,
                             String status, String errorMessage, Long costMs,
                             String traceId, String tenantId) {
        super(source == null ? new Object() : source);
        this.module = module;
        this.action = action;
        this.bizType = bizType;
        this.bizId = bizId;
        this.userId = userId;
        this.username = username;
        this.requestUrl = requestUrl;
        this.httpMethod = httpMethod;
        this.methodSignature = methodSignature;
        this.clientIp = clientIp;
        this.userAgent = userAgent;
        this.paramsJson = paramsJson;
        this.responseJson = responseJson;
        this.beforeData = beforeData;
        this.afterData = afterData;
        this.status = status;
        this.errorMessage = errorMessage;
        this.costMs = costMs;
        this.traceId = traceId;
        this.tenantId = tenantId;
    }
}
