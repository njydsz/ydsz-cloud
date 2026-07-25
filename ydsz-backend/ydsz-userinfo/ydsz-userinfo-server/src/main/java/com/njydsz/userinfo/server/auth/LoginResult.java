package com.njydsz.userinfo.server.auth;

import lombok.Data;

/**
 * 登录结果 DTO。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class LoginResult {
    private String accessToken;
    private String refreshToken;
    private long expiresIn;
    private String userId;
    private String username;
    private String realName;
}
