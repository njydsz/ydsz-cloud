package com.njydsz.userinfo.server.config;

import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;

import io.jsonwebtoken.security.Keys;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.njydsz.common.auth.oidc.JwksEndpoint;
import com.njydsz.common.auth.token.TokenProperties;

/**
 * OIDC 自动配置类
 *
 * <p>注册 OIDC 相关的 Bean，包括：
 *
 * <ul>
 *   <li>{@link OidcProperties} — 配置属性绑定
 *   <li>{@link JwksEndpoint} — JWKS 公钥端点
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Configuration
@EnableConfigurationProperties(OidcProperties.class)
@ConditionalOnProperty(
    prefix = "ydsz.userinfo.oidc",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class OidcConfiguration {

  /**
   * 注册 JWKS 公钥端点 Bean
   *
   * <p>从 TokenProperties 获取 HMAC 密钥构建 JWKS 端点，用于签发 JWKS 公钥 JSON。
   *
   * @param tokenProperties Token 配置属性（含 HMAC 密钥）
   * @return JWKS 公钥端点实例
   */
  @Bean
  public JwksEndpoint jwksEndpoint(TokenProperties tokenProperties) {
    byte[] secretBytes = tokenProperties.getSecretKey().getBytes(StandardCharsets.UTF_8);
    SecretKey secretKey = Keys.hmacShaKeyFor(secretBytes);
    return new JwksEndpoint(secretKey.getEncoded(), tokenProperties.getPublicKeyPem());
  }
}
