package com.njydsz.pmis.common.security;

import lombok.Builder;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 登录审计事件
 *
 * <p>由 AuthService 在登录成功/失败后发布，audit 模块的 LoginAuditListener 异步落库。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@Builder
public class LoginAuditEvent implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String username;
    private Long userId;
    private String loginIp;
    private String userAgent;
    private LoginStatus status;
    private String failReason;
    private Boolean mfaUsed;
    private Boolean mfaSuccess;
    private String traceId;
    private Long tenantId;
    private Long loginAt;
}
