package com.njydsz.common.cache.listener;

/**
 * 缓存删除原因枚举
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public enum RemovalCause {
  /** 显式删除 */
  EXPLICIT,
  /** 被替换 */
  REPLACED,
  /** 被 GC 回收 */
  COLLECTED,
  /** 过期 */
  EXPIRED,
  /** 超出容量限制 */
  SIZE
}
