package com.njydsz.common.util.security.crypto;

import java.util.concurrent.atomic.AtomicReference;

import lombok.extern.slf4j.Slf4j;

/**
 * 密钥来源注册表——{@link KeyProvider} SPI 的全局注册入口。
 *
 * <p>注册途径（二选一）：
 *
 * <ul>
 *   <li><b>Spring 环境（推荐）：</b>业务方声明 {@code KeyProvider} Bean，
 *       由 {@code CryptoAutoConfiguration} 自动注入注册
 *   <li><b>非 Spring 环境：</b>启动期调用 {@link #register(KeyProvider)} 手动注册
 * </ul>
 *
 * <p><b>替换语义：</b>后注册者覆盖先注册者（与"配置即生效"的运维直觉一致），
 * 与 {@code CryptoUtils.setDefaultAlgorithm} 的"仅允许一次"不同——密钥来源切换
 * （如 KMS 故障切配置中心）是合法运维动作，不应被一次性注入锁死。 注册动作打 info 日志留痕。
 *
 * <p><b>线程安全：</b>注册与解析均为无锁原子操作，密钥解析发生在请求路径上时不引入竞争。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see KeyProvider
 */
@Slf4j
public final class KeyProviderRegistry {

  /** 全局唯一的 KeyProvider 实例（null 表示未注册） */
  private static final AtomicReference<KeyProvider> REGISTERED = new AtomicReference<>();

  private KeyProviderRegistry() {
    throw new UnsupportedOperationException("Utility class");
  }

  /**
   * 注册密钥来源（后注册者覆盖先注册者）。
   *
   * @param provider KeyProvider 实现，不可为 null
   */
  public static void register(KeyProvider provider) {
    if (provider == null) {
      throw new IllegalArgumentException("KeyProvider must not be null");
    }
    KeyProvider previous = REGISTERED.getAndSet(provider);
    if (previous == null) {
      log.info("KeyProvider 已注册: {}", provider.getClass().getName());
    } else {
      log.info(
          "KeyProvider 已替换: {} -> {}", previous.getClass().getName(), provider.getClass().getName());
    }
  }

  /**
   * 注销当前密钥来源（主要用于测试复位）。
   */
  public static void unregister() {
    REGISTERED.set(null);
  }

  /**
   * 是否已注册密钥来源。
   *
   * @return true 表示已注册
   */
  public static boolean isRegistered() {
    return REGISTERED.get() != null;
  }

  /**
   * 按密钥标识解析密钥字节。
   *
   * @param keyId 密钥标识
   * @return 密钥字节数组
   * @throws CryptoException 未注册 KeyProvider，或提供方返回 null/抛出异常时
   */
  public static byte[] resolve(String keyId) {
    KeyProvider provider = REGISTERED.get();
    if (provider == null) {
      throw new CryptoException(
          "未注册 KeyProvider（密钥来源 SPI）。请声明 KeyProvider Bean 或调用"
              + " KeyProviderRegistry.register()，或直接使用传裸密钥的 API（如 encrypt(plaintext, key)）");
    }
    if (keyId == null || keyId.isEmpty()) {
      throw new CryptoException("keyId must not be null or empty");
    }
    try {
      byte[] key = provider.getKey(keyId);
      if (key == null) {
        throw new CryptoException("KeyProvider 返回 null 密钥, keyId: " + keyId);
      }
      return key;
    } catch (CryptoException e) {
      throw e;
    } catch (Exception e) {
      throw new CryptoException("解析密钥失败, keyId: " + keyId, e);
    }
  }
}
