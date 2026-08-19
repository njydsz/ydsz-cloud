package com.njydsz.userinfo.server.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.njydsz.userinfo.domain.config.MfaSecretEncryptor;
import com.njydsz.userinfo.infra.config.AesMfaSecretEncryptor;

/**
 * MFA 配置类。
 *
 * <p>根据 {@code ydsz.userinfo.mfa.encryption-key} 是否配置，决定注入 AES-256-GCM 加密器
 * （生产环境）还是回退到明文实现（开发环境，由 {@code PlainMfaSecretEncryptor} 的
 * {@code @ConditionalOnMissingBean} 自动兜底）。
 *
 * @author ydsz-team
 * @since 2.24.0
 */
@Configuration
public class MfaConfiguration {

  /**
   * 注册 AES-256-GCM MFA 密钥加密器。
   *
   * <p>仅当 {@code ydsz.userinfo.mfa.encryption-key} 已配置时生效。
   * 未配置时 {@link com.njydsz.userinfo.infra.config.PlainMfaSecretEncryptor}
   * 自动兜底（明文存储，适用于开发/测试环境）。
   *
   * @param properties 用户中心配置属性
   * @return AES-GCM 加密器
   */
  @Bean
  @ConditionalOnProperty(prefix = "ydsz.userinfo.mfa", name = "encryption-key")
  public MfaSecretEncryptor mfaSecretEncryptor(UserInfoProperties properties) {
    return new AesMfaSecretEncryptor(properties.getMfaEncryptionKey());
  }
}
