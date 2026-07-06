package com.njydsz.pmis.userinfo.service;

import com.njydsz.pmis.userinfo.dto.CaptchaVO;
import com.njydsz.pmis.userinfo.dto.LoginDTO;
import com.njydsz.pmis.userinfo.dto.LoginResultVO;

/**
 * 认证服务
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface AuthService {

    /**
     * 生成图形验证码
     *
     * @return 验证码 VO（含 captchaKey 与 Base64 图片）
     */
    CaptchaVO generateCaptcha();

    /**
     * 登录
     *
     * @param dto 登录请求参数（用户名、密码、验证码等）
     * @return 登录结果 VO（含访问 Token 与刷新 Token）
     * @throws BizException 当验证码错误、用户不存在、账号锁定或密码错误时抛出
     */
    LoginResultVO login(LoginDTO dto);

    /**
     * 刷新 Token
     *
     * @param refreshToken 刷新 Token
     * @return 新的登录结果 VO（含新的访问 Token 与刷新 Token）
     * @throws BizException 当刷新 Token 无效或用户不存在/禁用时抛出
     */
    LoginResultVO refresh(String refreshToken);

    /**
     * 登出
     *
     * @param userId 用户 ID
     */
    void logout(String userId);

    /**
     * 将 Token 加入黑名单（用于登出后防止 Token 继续使用）
     *
     * @param token         待拉黑的 Token
     * @param expireSeconds 黑名单有效期（秒），通常与 Token 剩余有效期一致
     */
    void blacklistToken(String token, long expireSeconds);
}
