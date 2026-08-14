package com.njydsz.common.cache.export;

/**
 * 缓存导出异常 — 当导出操作无法安全执行时抛出。
 *
 * <p>典型场景：缓存条目数超过序列化导出的安全上限可能导致 OOM。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class CacheExportException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  /**
   * 创建缓存导出异常
   *
   * @param message 异常信息
   */
  public CacheExportException(String message) {
    super(message);
  }

  /**
   * 创建缓存导出异常（带原因）
   *
   * @param message 异常信息
   * @param cause 根因
   */
  public CacheExportException(String message, Throwable cause) {
    super(message, cause);
  }
}
