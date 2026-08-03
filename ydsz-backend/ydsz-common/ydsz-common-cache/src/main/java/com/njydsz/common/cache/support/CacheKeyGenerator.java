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

    /**
     * 设置 key 的一级前缀，用于隔离不同业务域的缓存。
     *
     * <p>多个应用共用一套 Redis 时必须设置，否则不同模块的同名 key 会互相覆盖。
     * 前缀参与 SCAN 匹配模式的构造，因此选定后不宜随意变更，
     * 否则历史 key 将无法被批量清理。
     *
     * @param prefix 业务前缀，如 {@code "user"}；为 {@code null} 或空串表示不加前缀
     * @return 当前构建器，便于链式调用
     */
    public Builder prefix(String prefix) {
      this.prefix = prefix;
      return this;
    }

    /**
     * 设置 key 的二级命名空间，常用于承载版本号。
     *
     * <p>缓存值的结构发生不兼容变更时，递增命名空间（如 {@code v1} → {@code v2}）
     * 即可让全部旧 key 自然失效，无需停机清理，是灰度发布的常用手法。
     *
     * @param namespace 命名空间，如 {@code "v1"}；为 {@code null} 或空串表示不加命名空间
     * @return 当前构建器，便于链式调用
     */
    public Builder namespace(String namespace) {
      this.namespace = namespace;
      return this;
    }

    /**
     * 开启超长 key 的哈希压缩，默认关闭。
     *
     * <p>开启后，业务部分长度超过 {@link #maxLength(int)} 的 key 会被替换为
     * MD5 摘要的 URL-safe Base64 编码（固定 22 字符），
     * 以控制 Redis 内存占用与网络开销。
     *
     * <p><b>代价</b>：压缩<b>不可逆</b>，从 Redis 里看到的 key 无法还原为原始业务 key，
     * 排查问题时需要自行复算摘要；且 MD5 存在理论碰撞可能，
     * 极端情况下两个不同业务 key 会命中同一缓存条目。
     *
     * @param hashLongKeys {@code true} 开启压缩，{@code false} 保持原样
     * @return 当前构建器，便于链式调用
     */
    public Builder hashLongKeys(boolean hashLongKeys) {
      this.hashLongKeys = hashLongKeys;
      return this;
    }

    /**
     * 设置触发哈希压缩的 key 长度阈值，默认 128。
     *
     * <p>仅在 {@link #hashLongKeys(boolean)} 为 {@code true} 时生效，
     * 且只统计业务 key 本身的长度，<b>不含</b>前缀与命名空间。
     * MD5 不可用的兜底路径也会用该值对 key 做截断。
     *
     * @param maxLength 长度阈值（字符数），应大于 0
     * @return 当前构建器，便于链式调用
     */
    public Builder maxLength(int maxLength) {
      this.maxLength = maxLength;
      return this;
    }

    /**
     * 设置各层级之间的分隔符，默认为 {@code ":"}。
     *
     * <p>沿用 Redis 社区约定的冒号分层习惯可让 RedisInsight 等工具
     * 自动把 key 渲染成树形结构；改成其他字符会丧失这一便利。
     *
     * @param separator 分隔符；为 {@code null} 时回退为默认的 {@code ":"}
     * @return 当前构建器，便于链式调用
     */
    public Builder separator(String separator) {
      this.separator = separator;
      return this;
    }

    /**
     * 根据已设置的参数构造 {@link CacheKeyGenerator} 实例。
     *
     * <p>未显式设置的前缀、命名空间与分隔符分别回退为空串与默认的 {@code ":"}；
     * 此处不校验参数组合合法性，入参 null 契约在 {@link #generate(Object)} 调用时才生效。
     *
     * @return 组装完成的缓存 Key 生成器
     */
    public CacheKeyGenerator build() {
      return new CacheKeyGenerator(this);
    }
  }
}
