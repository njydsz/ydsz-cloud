package com.njydsz.common.safe.csrf;

/**
 * CSRF 令牌存储库接口
 *
 * <p>定义 CSRF 令牌的存储策略，支持自定义实现（如 Redis、内存等）。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public interface CsrfTokenRepository {

  /**
   * 创建 CSRF 令牌
   *
   * @param sessionId 会话 ID
   * @return CSRF 令牌
   */
  CsrfToken createToken(String sessionId);

  /**
   * 根据令牌值获取 CSRF 令牌
   *
   * @param token 令牌值
   * @return CSRF 令牌，不存在则返回 null
   */
  CsrfToken getToken(String token);

  /**
   * 验证令牌是否有效
   *
   * @param token 令牌值
   * @param sessionId 会话 ID
   * @return 是否有效
   */
  boolean validateToken(String token, String sessionId);

  /**
   * 删除令牌
   *
   * @param token 令牌值
   */
  void removeToken(String token);

  /**
   * 清除会话的所有令牌
   *
   * @param sessionId 会话 ID
   */
  void clearSession(String sessionId);
}
