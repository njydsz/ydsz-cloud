package com.njydsz.userinfo.infra.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import com.njydsz.userinfo.domain.config.MfaSecretEncryptor;

/**
 * 明文 MFA 密钥加密器（开发/测试环境默认实现）。
 *
 * <p>不做任何加密/解密操作，明文存入 Redis。仅用于开发测试环境，生产环境必须配置
 * {@code ydsz.userinfo.mfa.encryption-key} 以启用 {@link AesMfaSecretEncryptor}。
 *
 * <p><b>启用条件：</b>容器中不存在其他 {@link MfaSecretEncryptor} Bean
 * （即未配置 {@code ydsz.userinfo.mfa.encryption-key}）。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Component
@ConditionalOnMissingBean(MfaSecretEncryptor.class)
public class PlainMfaSecretEncryptor implements MfaSecretEncryptor {

  @Override
  public String encrypt(String plainSecret) {
    return plainSecret;
  }

  @Override
  public String decrypt(String cipherSecret) {
    return cipherSecret;
  }
}
