package com.njydsz.pmis.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 操作日志事件
 *
 * <p>由 OperationLogAspect 发布，audit 模块监听后持久化。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OperationLogEvent implements Serializable {

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 业务模块名称，如 project / execution / finance */
    private String module;
    /** 业务动作，如 create / update / delete / export */
    private String action;
    /** 业务类型，用于细分操作归类 */
    private String bizType;
    /** 业务主键 ID */
    private String bizId;
    /** 操作人用户 ID */
    private Long userId;
    /** 操作人用户名 */
    private String username;
    /** 请求 URL */
    private String requestUrl;
    /** HTTP 方法，如 GET / POST / PUT / DELETE */
    private String httpMethod;
    /** 方法签名（类#方法） */
    private String methodSignature;
    /** 客户端 IP */
    private String clientIp;
    /** User-Agent 头 */
    private String userAgent;
    /** 请求参数 JSON */
    private String paramsJson;
    /** 响应结果 JSON */
    private String responseJson;
    /** 操作状态，如 SUCCESS / FAIL */
    private String status;
    /** 错误信息（失败时填充） */
    private String errorMessage;
    /** 耗时（毫秒） */
    private Long costMs;
    /** 链路追踪 ID */
    private String traceId;
    /** 租户 ID */
    private Long tenantId;
    /** 操作时间戳（毫秒） */
    private Long timestamp;
}
