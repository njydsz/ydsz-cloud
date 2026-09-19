package com.njydsz.common.redis.tenant;

import java.util.function.Supplier;

import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * 租户级 Redis Key 前缀器。
 *
 * <p>为所有 Redis key 自动添加租户前缀，实现租户级数据隔离。 格式：{@code {tenantId}:{originalKey}}
 *
 * <p><b>使用场景：</b>
 *
 * <ul>
 *   <li>多租户 SaaS 系统中，不同租户的缓存数据需要隔离
 *   <li>分布式锁、限流计数器等需要按租户维度隔离
 *   <li>业务缓存 key 需要区分租户
 * </ul>
 *
 * <p><b>实现方式：</b> 通过包装 {@link RedisSerializer} 实现，在序列化 key 时自动添加租户前缀。 租户 ID 通过 {@link Supplier}
 * 注入，由调用方提供。
 *
 * <p><b>注意事项：</b>
 *
 * <ul>
 *   <li>超级管理员（tenantId = null 或 "0"）不添加前缀
 *   <li>仅对 key 序列化生效，value 不受影响
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public class TenantRedisKeyPrefixer {

  private final boolean isEnabled;
  private final Supplier<String> tenantIdSupplier;

  /**
   * 构造租户级 Redis Key 前缀器。
   *
   * @param tenantIdSupplier 租户 ID 提供者（通常为 () -> RequestContext.getTenantId()）
   * @param enabled 是否启用前缀
   */
  public TenantRedisKeyPrefixer(Supplier<String> tenantIdSupplier, boolean enabled) {
    this.tenantIdSupplier = tenantIdSupplier;
    this.isEnabled = enabled;
  }

  /** 租户前缀版本标识符，用于在反序列化时安全区分租户前缀与业务 Key 内容 */
  private static final String TENANT_PREFIX_MARKER = "t:";

  /**
   * 为 key 添加租户前缀。
   *
   * <p>前缀格式：{@code t:{tenantId}:{originalKey}}。使用 {@value #TENANT_PREFIX_MARKER}
   * 作为版本标识符，避免反序列化时因业务 Key 恰好以字母开头+冒号格式而导致误判剥离。
   *
   * @param key 原始 key
   * @return 带租户前缀的 key，如果未启用或为超级管理员则返回原 key
   */
  public String prefixKey(String key) {
    if (!isEnabled || key == null) {
      return key;
    }

    String tenantId = tenantIdSupplier != null ? tenantIdSupplier.get() : null;
    if (tenantId == null || "0".equals(tenantId)) {
      return key;
    }

    return TENANT_PREFIX_MARKER + tenantId + ":" + key;
  }

  /**
   * 从带租户前缀的完整 Key 中剥离租户前缀，还原为原始业务 Key。
   *
   * <p>仅在已知该 Key 确实添加了租户前缀时使用（如 SCAN 遍历时）。
   * 普通读/写场景无需调用此方法，因为 {@link #createKeySerializer()} 在序列化时自动加前缀。
   *
   * @param prefixedKey 带租户前缀的完整 Key
   * @return 原始业务 Key；如果前缀格式不匹配则返回原值
   */
  public String deprefixKey(String prefixedKey) {
    if (!isEnabled || prefixedKey == null) {
      return prefixedKey;
    }

    String tenantId = tenantIdSupplier != null ? tenantIdSupplier.get() : null;
    if (tenantId == null || "0".equals(tenantId)) {
      return prefixedKey;
    }

    String expectedPrefix = TENANT_PREFIX_MARKER + tenantId + ":";
    if (prefixedKey.startsWith(expectedPrefix)) {
      return prefixedKey.substring(expectedPrefix.length());
    }
    return prefixedKey;
  }

  /**
   * 创建租户感知的 Redis Key 序列化器。
   *
   * @return 包装后的 RedisSerializer
   */
  public RedisSerializer<String> createKeySerializer() {
    return new TenantAwareKeySerializer(this);
  }

  /** 租户感知的 Redis Key 序列化器。 */
  private static class TenantAwareKeySerializer implements RedisSerializer<String> {

    private final TenantRedisKeyPrefixer prefixer;
    private final StringRedisSerializer delegate = new StringRedisSerializer();

    TenantAwareKeySerializer(TenantRedisKeyPrefixer prefixer) {
      this.prefixer = prefixer;
    }

    @Override
    public byte[] serialize(String s) {
      String prefixedKey = prefixer.prefixKey(s);
      return delegate.serialize(prefixedKey);
    }

    @Override
    public String deserialize(byte[] bytes) {
      String key = delegate.deserialize(bytes);
      if (key == null) {
        return null;
      }
      // 使用安全的租户前缀剥离方法（基于 t:{tenantId}: 固定格式匹配，避免正则误判）
      return prefixer.deprefixKey(key);
    }
  }
}
