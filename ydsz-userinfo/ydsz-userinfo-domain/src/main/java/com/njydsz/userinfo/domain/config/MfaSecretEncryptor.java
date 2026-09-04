package com.njydsz.userinfo.domain.config;

import com.njydsz.userinfo.domain.config.AesMfaSecretEncryptor;

/**
 * MFA 密钥加密器（策略接口）。
 *
 * <p>定义 TOTP 密钥的加密/解密抽象，用于保护存储在 Redis 中的 MFA 共享密钥（secret）。
 *
 * <p><b>安全说明：</b>
 *
 * <ul>
 *   <li>明文存储（{@link PlainMfaSecretEncryptor}）仅用于开发/测试环境</li>
 *   <li>生产环境必须使用 AES-256-GCM 加密实现（{@link AesMfaSecretEncryptor}）</li>
 * </ul>
 *
 * <p><b>线程安全：</b>实现类必须无状态，可多线程并发调用。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public interface MfaSecretEncryptor {

  /**
   * 加密 MFA 共享密钥。
   *
   * @param plainSecret 明文密钥（Base32 编码）
   * @return 密文（Base64 编码），可直接存入 Redis
   * @throws IllegalArgumentException 明文为空时抛出
   */
  String encrypt(String plainSecret);

  /**
   * 解密 MFA 共享密钥。
   *
   * @param cipherSecret 密文（Base64 编码）
   * @return 明文密钥（Base32 编码），用于 TOTP 校验
   * @throws IllegalArgumentException 密文为空或解密失败时抛出
   */
  String decrypt(String cipherSecret);
}
