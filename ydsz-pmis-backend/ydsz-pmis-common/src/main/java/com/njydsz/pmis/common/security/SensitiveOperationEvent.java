package com.njydsz.pmis.common.security;

import lombok.Builder;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 敏感操作二次确认事件
 *
 * <p>由 {@code @RequireReAuth} 注解 AOP 发布，audit 模块异步落库。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@Builder
public class SensitiveOperationEvent implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long userId;
    private String username;
    private String operationCode;
    private String operationName;
    private String bizType;
    private String bizId;
    /** PASSWORD / TOTP / SMS */
    private String reAuthMethod;
    private String reAuthToken;
    private Long verifiedAt;
    private Long expireAt;
    private String clientIp;
    private String traceId;
    private Long tenantId;
}
