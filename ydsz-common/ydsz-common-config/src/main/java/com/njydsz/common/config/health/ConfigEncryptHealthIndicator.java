package com.njydsz.common.config.health;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;

/**
 * 配置加密健康指标
 *
 * <p>检查 Jasypt 配置加密的运行时状态：
 *
 * <ul>
 *   <li>主密码是否已配置（通过环境变量 {@code JASYPT_ENCRYPTOR_PASSWORD} 或 {@code jasypt.encryptor.password} 属性）
 *   <li>环境中是否存在 {@code ENC()} 格式的加密属性
 *   <li>密钥来源（环境变量 / 配置属性 / 未配置）
 * </ul>
 *
 * <h3>健康状态</h3>
 *
 * <ul>
 *   <li><b>UP</b>：主密码已配置，或无加密属性（无需解密）
 *   <li><b>DOWN</b>：存在 ENC() 加密属性但主密码未配置
 *   <li><b>UNKNOWN</b>：环境不可用
 * </ul>
 *
 * <h3>暴露信息</h3>
 *
 * <pre>{
 *   "status": "UP",
 *   "details": {
 *     "encryptorPasswordSource": "ENV_VARIABLE",
 *     "encryptedPropertyCount": 3,
 *     "encryptedProperties": ["spring.datasource.password", "spring.data.redis.password"]
 *   }
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class ConfigEncryptHealthIndicator implements HealthIndicator {

  private static final String ENC_PREFIX = "ENC(";
  private static final String ENC_SUFFIX = ")";
  private static final String JASYPT_PASSWORD_ENV = "JASYPT_ENCRYPTOR_PASSWORD";
  private static final String JASYPT_PASSWORD_PROPERTY = "jasypt.encryptor.password";

  /** details 中最多显示的属性数量 */
  private static final int MAX_DETAIL_ITEMS = 20;

  private final ConfigurableEnvironment environment;
  private final long cacheTtlMs;

  /** 健康检查结果缓存 */
  private final AtomicReference<HealthCheckResult> cache = new AtomicReference<>();

  public ConfigEncryptHealthIndicator(ConfigurableEnvironment environment, long cacheTtlMs) {
    this.environment = environment;
    this.cacheTtlMs = cacheTtlMs;
  }

  /**
   * 上报配置加密健康状态。
   *
   * <p>扫描环境中所有以 {@code ENC(...)} 包裹的加密属性，按以下规则判定健康：
   *
   * <ul>
   *   <li>环境不可用 → UNKNOWN（检查 environment 是否为 null）
   *   <li>不存在加密属性 → UP
   *   <li>存在加密属性但 Jasypt 主密码未配置（环境变量与配置项均缺失）→ DOWN，提示必须配置
   *   <li>存在加密属性且主密码就绪 → UP，附带密钥来源与加密属性数量
   * </ul>
   */
  @Override
  public Health health() {
    // 环境不可用时返回 UNKNOWN
    if (environment == null) {
      return Health.unknown()
          .withDetail("error", "ConfigurableEnvironment is not available")
          .build();
    }

    // 检查缓存（高频调用场景减少全量扫描）
    if (cacheTtlMs > 0) {
      HealthCheckResult cached = cache.get();
      long now = System.currentTimeMillis();
      if (cached != null && (now - cached.timestamp()) < cacheTtlMs) {
        return cached.health();
      }
    }

    // 检查密钥来源
    String keySource = resolveKeySource();
    int encryptedCount = countEncryptedProperties();
    Set<String> encryptedKeys = findEncryptedPropertyKeys();

    Health.Builder builder;
    if (encryptedCount == 0) {
      builder =
          Health.up()
              .withDetail("encryptorPasswordSource", keySource)
              .withDetail("encryptedPropertyCount", 0);
    } else if ("NOT_CONFIGURED".equals(keySource)) {
      builder =
          Health.down()
              .withDetail(
                  "error",
                  "Encrypted properties found but Jasypt master password is not configured");
    } else {
      builder =
          Health.up()
              .withDetail("encryptorPasswordSource", keySource)
              .withDetail("encryptedPropertyCount", encryptedCount);
      // 仅显示前 MAX_DETAIL_ITEMS 个属性名
      Set<String> displayKeys = new HashSet<>();
      int count = 0;
      for (String key : encryptedKeys) {
        if (count++ >= MAX_DETAIL_ITEMS) {
          displayKeys.add("... (" + (encryptedKeys.size() - MAX_DETAIL_ITEMS) + " more)");
          break;
        }
        displayKeys.add(key);
      }
      builder.withDetail("encryptedProperties", displayKeys);
    }

    Health result = builder.build();
    if (cacheTtlMs > 0) {
      cache.set(new HealthCheckResult(result, System.currentTimeMillis()));
    }
    return result;
  }

  /**
   * 健康检查结果缓存记录
   *
   * @param health 检查结果
   * @param timestamp 生成时间戳
   */
  private record HealthCheckResult(Health health, long timestamp) {}

  /**
   * 手动清除健康检查缓存
   *
   * <p>在主动配置刷新后可调用此方法强制下次重新扫描。
   */
  public void evictCache() {
    cache.set(null);
  }

  /**
   * 判断主密码的来源
   *
   * @return "ENV_VARIABLE" / "CONFIG_PROPERTY" / "NOT_CONFIGURED"
   */
  private String resolveKeySource() {
    String envKey = System.getenv(JASYPT_PASSWORD_ENV);
    if (envKey != null && !envKey.isBlank()) {
      return "ENV_VARIABLE";
    }

    String configKey = environment.getProperty(JASYPT_PASSWORD_PROPERTY);
    if (configKey != null && !configKey.isBlank()) {
      return "CONFIG_PROPERTY";
    }

    return "NOT_CONFIGURED";
  }

  /** 统计 ENC() 格式的属性数量 */
  private int countEncryptedProperties() {
    int count = 0;
    for (PropertySource<?> ps : environment.getPropertySources()) {
      if (ps instanceof EnumerablePropertySource<?> enumerable) {
        for (String key : enumerable.getPropertyNames()) {
          Object value = enumerable.getProperty(key);
          if (value instanceof String strValue && isEncrypted(strValue)) {
            count++;
          }
        }
      }
    }
    return count;
  }

  /** 查找所有 ENC() 格式属性的键名集合 */
  private Set<String> findEncryptedPropertyKeys() {
    Set<String> keys = new HashSet<>();
    for (PropertySource<?> ps : environment.getPropertySources()) {
      if (ps instanceof EnumerablePropertySource<?> enumerable) {
        for (String key : enumerable.getPropertyNames()) {
          Object value = enumerable.getProperty(key);
          if (value instanceof String strValue && isEncrypted(strValue)) {
            keys.add(key);
          }
        }
      }
    }
    return keys;
  }

  private boolean isEncrypted(String value) {
    return value != null && value.startsWith(ENC_PREFIX) && value.endsWith(ENC_SUFFIX);
  }
}
