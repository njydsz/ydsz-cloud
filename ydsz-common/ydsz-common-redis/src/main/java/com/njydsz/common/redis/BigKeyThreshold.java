package com.njydsz.common.redis;

import java.io.Serializable;

/**
 * Redis 大 Key 检测阈值配置
 *
 * <p>封装各数据类型的大 Key 判定阈值，支持自定义配置。默认值：
 *
 * <ul>
 *   <li>String 值大小阈值：10KB（10240 字节）
 *   <li>Hash / Set / List / ZSet 元素数量阈值：10000 成员
 *   <li>热 Key 访问次数阈值：100 次
 * </ul>
 *
 * <p>使用 {@link #builder()} 进行自定义配置，或使用 {@link #defaults()} 获取默认阈值。
 *
 * @author ydsz-team
 * @since 26.10.01
 */
public class BigKeyThreshold implements Serializable {

  private static final long serialVersionUID = 1L;

  /** String 值大小阈值（字节），默认 10KB */
  private final long stringValueBytesThreshold;

  /** 集合类型（Hash/Set/List/ZSet）成员数量阈值，默认 10000 */
  private final int collectionMembersThreshold;

  /** 热 Key 访问次数阈值，默认 100 */
  private final int hotKeyAccessCountThreshold;

  /**
   * 默认 String 值大小阈值（10KB）
   */
  public static final long DEFAULT_STRING_BYTES_THRESHOLD = 10 * 1024;

  /**
   * 默认集合成员数量阈值（10000）
   */
  public static final int DEFAULT_COLLECTION_MEMBERS_THRESHOLD = 10000;

  /**
   * 默认热 Key 访问次数阈值（100）
   */
  public static final int DEFAULT_HOT_KEY_ACCESS_COUNT_THRESHOLD = 100;

  /**
   * 构造阈值配置
   *
   * @param stringValueBytesThreshold String 值大小阈值（字节）
   * @param collectionMembersThreshold 集合类型成员数量阈值
   * @param hotKeyAccessCountThreshold 热 Key 访问次数阈值
   */
  public BigKeyThreshold(
      long stringValueBytesThreshold,
      int collectionMembersThreshold,
      int hotKeyAccessCountThreshold) {
    this.stringValueBytesThreshold = stringValueBytesThreshold;
    this.collectionMembersThreshold = collectionMembersThreshold;
    this.hotKeyAccessCountThreshold = hotKeyAccessCountThreshold;
  }

  /**
   * 获取默认阈值配置
   *
   * @return 默认阈值实例（String 10KB、集合 10000 成员、热 Key 100 次）
   */
  public static BigKeyThreshold defaults() {
    return new BigKeyThreshold(
        DEFAULT_STRING_BYTES_THRESHOLD,
        DEFAULT_COLLECTION_MEMBERS_THRESHOLD,
        DEFAULT_HOT_KEY_ACCESS_COUNT_THRESHOLD);
  }

  /**
   * 创建阈值构建器
   *
   * @return 构建器实例
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * 获取 String 值大小阈值（字节）
   *
   * @return String 阈值（字节）
   */
  public long getStringValueBytesThreshold() {
    return stringValueBytesThreshold;
  }

  /**
   * 获取集合类型（Hash/Set/List/ZSet）成员数量阈值
   *
   * @return 集合成员数量阈值
   */
  public int getCollectionMembersThreshold() {
    return collectionMembersThreshold;
  }

  /**
   * 获取热 Key 访问次数阈值
   *
   * @return 热 Key 访问次数阈值
   */
  public int getHotKeyAccessCountThreshold() {
    return hotKeyAccessCountThreshold;
  }

  /**
   * {@link BigKeyThreshold} 构建器
   *
   * <p>提供流式 API 自定义各类型阈值，未设置的字段使用默认值。
   */
  public static class Builder {

    private long stringValueBytesThreshold = DEFAULT_STRING_BYTES_THRESHOLD;

    private int collectionMembersThreshold = DEFAULT_COLLECTION_MEMBERS_THRESHOLD;

    private int hotKeyAccessCountThreshold = DEFAULT_HOT_KEY_ACCESS_COUNT_THRESHOLD;

    /**
     * 设置 String 值大小阈值（字节）
     *
     * @param bytes 字节数，必须为正数
     * @return 当前构建器
     */
    public Builder stringValueBytesThreshold(long bytes) {
      this.stringValueBytesThreshold = bytes;
      return this;
    }

    /**
     * 设置集合类型成员数量阈值
     *
     * @param count 成员数量，必须为正数
     * @return 当前构建器
     */
    public Builder collectionMembersThreshold(int count) {
      this.collectionMembersThreshold = count;
      return this;
    }

    /**
     * 设置热 Key 访问次数阈值
     *
     * @param count 访问次数，必须为正数
     * @return 当前构建器
     */
    public Builder hotKeyAccessCountThreshold(int count) {
      this.hotKeyAccessCountThreshold = count;
      return this;
    }

    /**
     * 构建阈值配置实例
     *
     * @return 不可变的 {@link BigKeyThreshold} 实例
     */
    public BigKeyThreshold build() {
      return new BigKeyThreshold(
          stringValueBytesThreshold, collectionMembersThreshold, hotKeyAccessCountThreshold);
    }
  }
}
