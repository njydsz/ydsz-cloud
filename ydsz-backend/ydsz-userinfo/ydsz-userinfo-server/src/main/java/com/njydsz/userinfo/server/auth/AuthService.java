package com.njydsz.userinfo.server.auth;

/**
 * 认证服务接口。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface AuthService {

    LoginResult login(String username, String password);
    void logout(String accessToken);
    LoginResult refresh(String refreshToken);
}
