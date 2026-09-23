package com.njydsz.system.server.service;

import com.njydsz.system.domain.vo.SecondaryAuthVO;

/**
 * 二次身份验证服务接口。
 *
 * <p>提供敏感操作前的密码二次确认能力。验证当前登录用户的密码后，颁发一个短期有效的二次认证令牌，
 * 后续请求头 {@code X-Secondary-Auth} 携带该令牌以通过校验。
 *
 * @author ydsz-team
 * @since 26.09.08
 */
public interface SecondaryAuthService {

  /**
   * 发起二次身份验证：校验密码，通过后颁发令牌。
   *
   * @param userId 当前登录用户 ID
   * @param password 明文密码（HTTPS 传输）
   * @param scene 场景标识（如 config-edit / dict-delete），用于审计
   * @return 二次认证令牌（含 Token 和过期时间）
   * @throws com.njydsz.common.exception.custom.BusinessException 密码错误/用户不存在/账号锁定时抛出
   */
  SecondaryAuthVO verify(String userId, String password, String scene);

  /**
   * 校验二次认证令牌是否有效。
   *
   * @param userId 用户 ID
   * @param token 待验证的令牌
   * @return true 表示令牌有效且未过期；false 表示无效或已过期
   */
  boolean isTokenValid(String userId, String token);
}
