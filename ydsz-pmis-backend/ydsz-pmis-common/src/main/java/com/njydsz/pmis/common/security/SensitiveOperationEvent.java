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

    /** 序列化版本号 */
    @Serial
    private static final long serialVersionUID = 1L;

    /** 操作用户 ID */
    private Long userId;

    /** 操作用户名 */
    private String username;

    /** 操作码 */
    private String operationCode;

    /** 操作名 */
    private String operationName;

    /** 业务类型 */
    private String bizType;

    /** 业务 ID */
    private String bizId;

    /** 二次认证方式: PASSWORD / TOTP / SMS */
    private String reAuthMethod;

    /** 二次认证 token */
    private String reAuthToken;

    /** 校验通过时间戳（毫秒） */
    private Long verifiedAt;

    /** 二次认证 token 过期时间戳（毫秒） */
    private Long expireAt;

    /** 客户端 IP */
    private String clientIp;

    /** 链路追踪 ID */
    private String traceId;

    /** 租户 ID */
    private Long tenantId;
}
