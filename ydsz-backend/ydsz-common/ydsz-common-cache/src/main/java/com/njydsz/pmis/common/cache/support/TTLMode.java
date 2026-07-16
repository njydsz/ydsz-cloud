package com.njydsz.common.cache.support;

/**
 * TTL 缓存模式枚举
 *
 * @since 1.0.0
 * 
 */
public enum TTLMode {
  /** 基于写入时间 */
  WRITE,
  /** 基于访问时间 */
  ACCESS
}
