package com.njydsz.common.cache.support;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Objects;

/**
 * 缓存 Key 生成器 — 统一的 Key 生成和前缀策略
 *
 * <p>提供以下功能：
 *
 * <ul>
 *   <li>Key 前缀隔离：不同缓存实例使用不同前缀，避免 key 冲突
 *   <li>Key 哈希压缩：长 key 自动 MD5 压缩，减少内存占用
 *   <li>命名空间支持：支持多级命名空间（ns1:ns2:key）
 * </ul>
 *
 * <p>使用示例：
 *
 * <pre>{@code
 * CacheKeyGenerator generator = CacheKeyGenerator.builder()
 *     .prefix("user")
 *     .namespace("v1")
 *     .hashLongKeys(true)
 *     .maxLength(128)
 *     .build();
 *
 * String redisKey = generator.generate("userId:12345");
 * // 结果: "user:v1:userId:12345" 或哈希后的 "user:v1:aBcDeFg..."
 * }</pre>
 *
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class CacheKeyGenerator {

  private final String prefix;
  private final String namespace;
  private final boolean hashLongKeys;
  private final int maxLength;
  private final String separator;

  private CacheKeyGenerator(Builder builder) {
    this.prefix = builder.prefix != null ? builder.prefix : "";
    this.namespace = builder.namespace != null ? builder.namespace : "";
    this.hashLongKeys = builder.hashLongKeys;
    this.maxLength = builder.maxLength;
    this.separator = builder.separator != null ? builder.separator : ":";
  }

  /**
   * 生成缓存 key
   *
   * @param key 原始 key
   * @return 处理后的缓存 key
   */
  public String generate(Object key) {
    Objects.requireNonNull(key, "key must not be null");
    String keyStr = key.toString();

    StringBuilder sb = new StringBuilder();
    if (!prefix.isEmpty()) {
      sb.append(prefix).append(separator);
    }
    if (!namespace.isEmpty()) {
      sb.append(namespace).append(separator);
    }

    // 如果 key 过长且启用了哈希压缩，使用 MD5 哈希
    if (hashLongKeys && keyStr.length() > maxLength) {
      sb.append(hashKey(keyStr));
    } else {
      sb.append(keyStr);
    }

    return sb.toString();
  }

  /** 批量生成缓存 key 前缀（用于 SCAN 匹配） */
  public String generatePrefixPattern() {
    StringBuilder sb = new StringBuilder();
    if (!prefix.isEmpty()) {
      sb.append(prefix).append(separator);
    }
    if (!namespace.isEmpty()) {
      sb.append(namespace).append(separator);
    }
    sb.append("*");
    return sb.toString();
  }

  /** 使用 MD5 哈希 key */
  private String hashKey(String key) {
    try {
      MessageDigest md = MessageDigest.getInstance("MD5");
      byte[] hash = md.digest(key.getBytes(StandardCharsets.UTF_8));
      return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
    } catch (Exception e) {
      // 如果 MD5 不可用，截断 key
      return key.length() > maxLength ? key.substring(0, maxLength) : key;
    }
  }

  /** 获取前缀 */
  public String getPrefix() {
    return prefix;
  }

  /** 获取命名空间 */
  public String getNamespace() {
    return namespace;
  }

  /** 创建 Builder */
  public static Builder builder() {
    return new Builder();
  }

  /** CacheKeyGenerator 构建器 */
  public static final class Builder {
    private String prefix;
    private String namespace;
    private boolean hashLongKeys = false;
    private int maxLength = 128;
    private String separator;

    private Builder() {}

    public Builder prefix(String prefix) {
      this.prefix = prefix;
      return this;
    }

    public Builder namespace(String namespace) {
      this.namespace = namespace;
      return this;
    }

    public Builder hashLongKeys(boolean hashLongKeys) {
      this.hashLongKeys = hashLongKeys;
      return this;
    }

    public Builder maxLength(int maxLength) {
      this.maxLength = maxLength;
      return this;
    }

    public Builder separator(String separator) {
      this.separator = separator;
      return this;
    }

    public CacheKeyGenerator build() {
      return new CacheKeyGenerator(this);
    }
  }
}
