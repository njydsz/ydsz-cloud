package com.njydsz.userinfo.server.auth;

import com.njydsz.userinfo.domain.dto.LoginDTO;
import com.njydsz.userinfo.domain.vo.LoginVO;

/**
 * 认证服务接口。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface AuthService {

    /**
     * 用户登录。
     *
     * @param loginDTO 登录请求（含用户名、密码、验证码等）
     * @return 登录结果
     */
    LoginVO login(LoginDTO loginDTO);

    /**
     * 用户登出。
     *
     * @param accessToken 访问令牌
     */
    void logout(String accessToken);

    /**
     * 刷新 Token。
     *
     * @param refreshToken 刷新令牌
     * @return 新的登录结果
     */
    LoginVO refresh(String refreshToken);
}
