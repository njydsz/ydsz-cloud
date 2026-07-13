package com.njydsz.pmis.common.audit.event;

import java.io.Serializable;

import lombok.Data;

/**
 * 操作日志事件。
 *
 * <p>由 {@code @OperationLog} 注解切面发布，由 {@code OperationLogListener} 异步消费落库。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
public class OperationLogEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 模块 */
    private String module;
    /** 操作 */
    private String action;
    /** 业务类型 */
    private String bizType;
    /** 业务 ID */
    private String bizId;
    /** 用户 ID */
    private String userId;
    /** 用户名 */
    private String username;
    /** 请求 URL */
    private String requestUrl;
    /** HTTP 方法 */
    private String httpMethod;
    /** 方法签名 */
    private String methodSignature;
    /** 客户端 IP */
    private String clientIp;
    /** User-Agent */
    private String userAgent;
    /** 参数 JSON */
    private String paramsJson;
    /** 响应 JSON */
    private String responseJson;
    /** 变更前数据 */
    private String beforeData;
    /** 变更后数据 */
    private String afterData;
    /** 状态 */
    private String status;
    /** 错误消息 */
    private String errorMessage;
    /** 耗时（毫秒） */
    private Long costMs;
    /** 链路追踪 ID */
    private String traceId;
    /** 租户 ID */
    private String tenantId;
}
