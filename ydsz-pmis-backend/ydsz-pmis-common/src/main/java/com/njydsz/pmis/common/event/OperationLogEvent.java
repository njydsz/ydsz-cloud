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

    @Serial
    private static final long serialVersionUID = 1L;

    private String module;
    private String action;
    private String bizType;
    private String bizId;
    private Long userId;
    private String username;
    private String requestUrl;
    private String httpMethod;
    private String methodSignature;
    private String clientIp;
    private String userAgent;
    private String paramsJson;
    private String responseJson;
    private String status;
    private String errorMessage;
    private Long costMs;
    private String traceId;
    private Long tenantId;
    private Long timestamp;
}
