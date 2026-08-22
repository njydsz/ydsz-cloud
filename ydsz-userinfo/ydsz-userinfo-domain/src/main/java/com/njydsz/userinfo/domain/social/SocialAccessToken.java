package com.njydsz.userinfo.domain.social;

import java.io.Serializable;

/**
 * 社交访问令牌值对象。
 *
 * <p>封装 OAuth2 令牌端点返回的访问令牌信息，不可变。由 {@link SocialAuthProvider#exchangeToken}
 * 返回，传递给 {@link SocialAuthProvider#getUserInfo} 使用。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @param accessToken 访问令牌（平台 API 调用凭证）
 * @param refreshToken 刷新令牌（用于续期 access_token，可为 null）
 * @param expiresIn 访问令牌有效期（秒，从获取时刻起算）
 * @param openId 平台用户唯一标识
 * @param unionId 平台统一应用标识（跨平台同一用户的关联 ID，可为 null）
 */
public record SocialAccessToken(
    String accessToken,
    String refreshToken,
    long expiresIn,
    String openId,
    String unionId)
    implements Serializable {

  private static final long serialVersionUID = 1L;
}
