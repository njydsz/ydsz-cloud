package com.njydsz.common.safe.encrypt;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 字段加密自动配置
 *
 * <p>启用后，支持通过 {@link EncryptField} 注解和 {@link EncryptTypeHandler} 实现字段级加密。
 *
 * <p>配置示例：
 *
 * <pre>
 * ydsz:
 *   safe:
 *     field-encryption:
 *       enabled: true
 *       default-key-version: 1
 *       keys:
 *         1: "base64-encoded-32-byte-key..."
 * </pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@AutoConfiguration
@ConditionalOnProperty(
    prefix = "ydsz.safe.field-encryption",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
@EnableConfigurationProperties(EncryptFieldProperties.class)
public class FieldEncryptionAutoConfiguration {

  /**
   * 注册字段加密服务
   *
   * @param properties 加密配置
   * @return FieldEncryptionService 实例
   */
  @Bean
  @ConditionalOnMissingBean(FieldEncryptionService.class)
  public FieldEncryptionService fieldEncryptionService(EncryptFieldProperties properties) {
    if (properties.getKeys().isEmpty()) {
      throw new IllegalStateException("字段加密已启用但未配置密钥，请配置 ydsz.safe.field-encryption.keys");
    }
    FieldEncryptionService service =
        new FieldEncryptionService(properties.getKeys(), properties.getDefaultKeyVersion());
    EncryptTypeHandler.setEncryptionService(service);
    EncryptTypeHandler.setFailureStrategy(
        properties.getFailureStrategy(), properties.getMaskedValue());
    return service;
  }
}
