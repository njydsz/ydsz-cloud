package com.njydsz.pmis.common.security;

import java.io.Serial;
import java.io.Serializable;

import lombok.Builder;
import lombok.Data;

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

    /** 用户名 */
    private String username;

    /** 用户 ID */
    private String userId;

    /** 登录 IP */
    private String loginIp;

    /** User-Agent */
    private String userAgent;

    /** 登录结果状态 */
    private LoginStatus status;

    /** 失败原因 */
    private String failReason;

    /** 是否使用 MFA */
    private Boolean mfaUsed;

    /** MFA 是否校验成功 */
    private Boolean mfaSuccess;

    /** 链路追踪 ID */
    private String traceId;

    /** 租户 ID */
    private String tenantId;

    /** 登录时间戳（毫秒） */
    private Long loginAt;
}
