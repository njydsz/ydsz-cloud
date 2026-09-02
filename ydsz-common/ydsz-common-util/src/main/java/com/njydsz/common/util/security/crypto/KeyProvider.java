package com.njydsz.common.util.security.crypto;

/**
 * 密钥来源 SPI——收敛密钥获取路径的统一扩展点。
 *
 * <p><b>设计意图：</b>{@code CryptoUtils} 的底层 API 只收裸 {@code byte[]} 密钥，
 * 密钥来源（配置中心/环境变量/KMS/Vault）原本完全交给业务方散落实现。 本接口提供可选的统一扩展点：业务方实现该接口并注册为 Spring Bean
 * （或通过 {@link KeyProviderRegistry#register} 手动注册）， 即可让所有密钥获取收敛到单一入口，满足审计与轮转需求。
 *
 * <h2>实现示例（配置中心来源）</h2>
 *
 * <pre>{@code
 * @Component
 * public class ConfigCenterKeyProvider implements KeyProvider {
 *
 *   @Override
 *   public byte[] getKey(String keyId) {
 *     String base64Key = configClient.get("crypto.keys." + keyId);
 *     return Base64.getDecoder().decode(base64Key);
 *   }
 * }
 * }</pre>
 *
 * <p><b>安全建议：</b>
 *
 * <ul>
 *   <li>实现方返回的密钥字节数组仍由调用方持有，敏感场景建议结合
 *       {@code CryptoUtils.wipeKey(byte[])} 在使用后擦除
 *   <li>keyId 应使用无业务语义的标识（如 {@code user-profile-v3}），禁止将密钥用途之外的信息编入
 *   <li>实现必须线程安全（密钥解析常发生在请求路径上）
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see KeyProviderRegistry
 */
@FunctionalInterface
public interface KeyProvider {

  /**
   * 获取指定密钥标识对应的密钥字节。
   *
   * <p>返回的数组长度必须与当前算法的密钥长度匹配（如 AES-256-GCM 需 32 字节）， 不匹配时由加密层抛出
   * {@link IllegalArgumentException}。
   *
   * @param keyId 密钥标识（业务方自定义，如 {@code "user-profile-v3"}）
   * @return 密钥字节数组；不可返回 null
   * @throws CryptoException 密钥不存在或获取失败时抛出（携带 keyId 便于排障）
   */
  byte[] getKey(String keyId);
}
