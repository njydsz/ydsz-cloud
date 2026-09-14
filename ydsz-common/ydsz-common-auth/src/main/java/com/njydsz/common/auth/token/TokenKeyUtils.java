package com.njydsz.common.auth.token;

import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;

import io.jsonwebtoken.security.Keys;

/**
 * Token 密钥构建工具 — common-auth 统一入口（P2-5 整改收敛）。
 *
 * <p>此前 userinfo 等业务模块为构建 HMAC 密钥直连 jjwt {@code Keys} 工具类，
 * 与 {@link JwtTokenService} 内部实现重复。本类收敛密钥构建语义：
 * 业务模块禁止直接 import {@code io.jsonwebtoken.security.Keys}，
 * 统一经本工具（或直接注入 {@link TokenService}）获取密钥。
 *
 * @author ydsz-team
 * @since 26.09.14
 */
public final class TokenKeyUtils {

  /** 工具类禁止实例化 */
  private TokenKeyUtils() {
    throw new UnsupportedOperationException("utility class");
  }

  /**
   * 由原始密钥字符串构建 HMAC-SHA 签名密钥。
   *
   * @param secretKeyRaw 原始密钥字符串（与 {@code TokenProperties#getSecretKey()} 同源）
   * @return HMAC-SHA 签名密钥
   */
  public static SecretKey hmacShaKeyFor(String secretKeyRaw) {
    return Keys.hmacShaKeyFor(secretKeyRaw.getBytes(StandardCharsets.UTF_8));
  }
}
