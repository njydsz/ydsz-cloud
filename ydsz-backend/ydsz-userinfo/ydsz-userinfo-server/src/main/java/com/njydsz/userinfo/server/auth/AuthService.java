package com.njydsz.userinfo.server.auth;

import com.njydsz.userinfo.domain.vo.LoginVO;

/**
 * 认证服务接口。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface AuthService {

    LoginVO login(String username, String password);
    void logout(String accessToken);
    LoginVO refresh(String refreshToken);
}
