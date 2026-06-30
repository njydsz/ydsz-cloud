package com.njydsz.pmis.auth.service;

import com.njydsz.pmis.auth.dto.CaptchaVO;
import com.njydsz.pmis.auth.dto.LoginDTO;
import com.njydsz.pmis.auth.dto.LoginResultVO;

/**
 * 认证服务
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface AuthService {

    /**
     * 生成图形验证码
     */
    CaptchaVO generateCaptcha();

    /**
     * 登录
     */
    LoginResultVO login(LoginDTO dto);

    /**
     * 刷新 Token
     */
    LoginResultVO refresh(String refreshToken);

    /**
     * 登出
     */
    void logout(String userId);
}
