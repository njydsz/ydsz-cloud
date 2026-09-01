package com.njydsz.common.util.security.crypto;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * 加密算法自动配置——将 {@link CryptoProperties} 桥接进 {@link CryptoUtils}。
 *
 * <p>通过 {@code ydsz.util.crypto.default-algorithm} 属性配置默认加密算法， 避免再依赖系统属性 {@code
 * crypto.algorithm}（两者可并存，系统属性优先级更高）。
 *
 * <p>同时桥接可选的密钥来源 SPI：业务方声明 {@link KeyProvider} Bean 即可注册， 供 {@code
 * CryptoUtils.encryptWithKeyId} 等密钥标识 API 使用（密钥来源收敛，见 F-3 治理项）。
 *
 * <p>示例：
 *
 * <pre>{@code
 * ydsz:
 *   util:
 *     crypto:
 *       default-algorithm: SM4-GCM
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see CryptoProperties
 * @see CryptoUtils
 * @see KeyProviderRegistry
 */
@AutoConfiguration
@EnableConfigurationProperties(CryptoProperties.class)
public class CryptoAutoConfiguration {

  /**
   * 注入默认算法并桥接可选的 {@link KeyProvider} Bean（密钥来源 SPI）。
   *
   * @param properties 加密算法配置属性
   * @param keyProvider 密钥来源提供者（可选，业务方声明 Bean 即生效）
   */
  public CryptoAutoConfiguration(
      CryptoProperties properties, ObjectProvider<KeyProvider> keyProvider) {
    CryptoUtils.setDefaultAlgorithm(properties.getDefaultAlgorithm());
    KeyProvider provider = keyProvider.getIfAvailable();
    if (provider != null) {
      KeyProviderRegistry.register(provider);
    }
  }
}
