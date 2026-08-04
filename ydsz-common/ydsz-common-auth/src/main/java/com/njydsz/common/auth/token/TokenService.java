package com.njydsz.common.auth.token;

import com.njydsz.common.auth.model.UserInfo;

/**
 * Token 服务接口
 *
 * <p>定义 Token 生命周期管理标准规范，包括：
 * <ul>
 *   <li>Token 签发（access_token + refresh_token 双令牌机制）</li>
 *   <li>Token 验证（签名校验 + 过期检查）</li>
 *   <li>Token 刷新（基于 refresh_token 换取新令牌）</li>
 *   <li>Token 解析（从令牌中提取用户信息）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * 
 */
public interface TokenService {

    /**
     * 签发访问令牌
     *
     * @param userInfo 用户信息
     * @return 访问令牌（JWT 格式）
     */
    String issueAccessToken(UserInfo userInfo);

    /**
     * 签发刷新令牌
     *
     * @param userInfo 用户信息
     * @return 刷新令牌（JWT 格式）
     */
    String issueRefreshToken(UserInfo userInfo);

    /**
     * 验证访问令牌
     *
     * @param token 访问令牌
     * @return 验证通过返回 true，否则返回 false
     */
    boolean validateAccessToken(String token);

    /**
     * 验证刷新令牌
     *
     * @param token 刷新令牌
     * @return 验证通过返回 true，否则返回 false
     */
    boolean validateRefreshToken(String token);

    /**
     * 从访问令牌解析用户信息
     *
     * @param token 访问令牌
     * @return 用户信息，解析失败返回 null
     */
    UserInfo parseAccessToken(String token);

    /**
     * 从刷新令牌解析用户信息
     *
     * @param token 刷新令牌
     * @return 用户信息，解析失败返回 null
     */
    UserInfo parseRefreshToken(String token);

    /**
     * 刷新令牌（使用 refresh_token 换取新的 access_token）
     *
     * @param refreshToken 刷新令牌
     * @return 新的访问令牌，刷新失败返回 null
     */
    String refreshAccessToken(String refreshToken);
}
