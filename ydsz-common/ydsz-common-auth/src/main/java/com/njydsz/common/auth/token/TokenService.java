package com.njydsz.common.auth.token;

import com.njydsz.common.auth.model.UserInfo;

/**
 * Token 服务接口
 *
 * <p>定义 Token 生命周期管理标准规范，包括：
 *
 * <ul>
 *   <li>Token 签发（access_token + refresh_token 双令牌机制）
 *   <li>Token 验证（签名校验 + 过期检查）
 *   <li>Token 刷新（基于 refresh_token 换取新令牌）
 *   <li>Token 解析（从令牌中提取用户信息）
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
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

  /**
   * 获取访问令牌剩余有效时间（秒）（P1-2 Token 自动续签）。
   *
   * <p>用于判断 Token 是否临近过期，当剩余有效期低于阈值时自动续签。
   *
   * @param token 访问令牌
   * @return 剩余有效时间（秒）；令牌无效或已过期返回 0
   */
  long getAccessTokenRemainingTtl(String token);

  /**
   * 签发 OIDC ID Token
   *
   * <p>ID Token 是 OpenID Connect 协议的核心令牌，用于向客户端证明用户身份。 包含标准 OIDC 声明（iss, sub, aud, exp, iat, nonce）， 有效期较短（默认
   * 10 分钟，可通过 ydsz.auth.token.id-token-expire-seconds 配置）。
   *
   * @param userInfo 用户信息（sub 声明来源）
   * @param nonce    一次性随机值（可选，用于防止重放攻击，可为 null）
   * @param clientId 客户端 ID（aud 声明来源）
   * @return ID Token（JWT 格式），签发失败返回 null
   */
  String issueIdToken(UserInfo userInfo, String nonce, String clientId);
}
