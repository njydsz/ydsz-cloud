package com.njydsz.pmis.userinfo.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 登录结果
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResult implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 访问 token */
    private String accessToken;

    /** 刷新 token */
    private String refreshToken;

    /** token 过期时间（毫秒） */
    private Long expireAt;

    /** 会话 ID */
    private String sessionId;

    /** 用户 ID */
    private String userId;

    /** 用户名 */
    private String username;

    /** 是否需要 2FA 二次验证 */
    private boolean mfaRequired;

    /** 2FA 已通过 */
    private boolean mfaPassed;

    /** 数据权限范围 */
    private String dataScope;
}
