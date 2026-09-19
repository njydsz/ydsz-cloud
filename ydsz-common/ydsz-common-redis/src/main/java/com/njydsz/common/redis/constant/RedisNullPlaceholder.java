package com.njydsz.common.redis.constant;

/**
 * Redis 空值占位符常量
 *
 * <p>统一系统中所有空值缓存标记的定义（原先散落在 {@code RedisStringOps.NullPlaceholder}、
 * {@code YdszCacheableAspect} 硬编码 {@code "NULL"}、{@code NullValueCacheHelper.__NULL__} 三处），
 * 确保空值标记的语义和取值全局唯一。
 *
 * <p>使用 {@link #MARKER} 作为占位符写入缓存，使用 {@link #isMarker(Object)} 判断缓存值是否为空标记。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public final class RedisNullPlaceholder {

  /** 空值标记字符串，写入缓存以标识"已查询但无数据"的状态 */
  public static final String MARKER = "$YDSZ_NULL$";

  private RedisNullPlaceholder() {
    throw new UnsupportedOperationException("常量类禁止实例化");
  }

  /**
   * 判断给定缓存值是否为空值占位符
   *
   * @param cachedValue 缓存中的原始值
   * @return true-为空值占位符（表示底层无数据）
   */
  public static boolean isMarker(Object cachedValue) {
    return MARKER.equals(cachedValue);
  }
}
