package com.njydsz.common.cache.support;

/**
 * 缓存加载异常
 *
 * <p>封装缓存加载过程中发生的各类错误，包括数据源访问失败、反序列化失败、超时等场景。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class CacheLoadException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  /** 缓存键（可为 null） */
  private final Object key;

  /** 操作类型（如 "LOAD", "LOAD_ALL"） */
  private final String operation;

  /**
   * 构造缓存加载异常
   *
   * @param message 错误消息
   */
  public CacheLoadException(String message) {
    super(message);
    this.key = null;
    this.operation = null;
  }

  /**
   * 构造缓存加载异常（带原始异常）
   *
   * @param message 错误消息
   * @param cause 原始异常
   */
  public CacheLoadException(String message, Throwable cause) {
    super(message, cause);
    this.key = null;
    this.operation = null;
  }

  /**
   * 构造缓存加载异常（带上下文）
   *
   * @param key 缓存键
   * @param operation 操作类型
   * @param cause 原始异常
   */
  public CacheLoadException(Object key, String operation, Throwable cause) {
    super(
        String.format(
            "缓存加载失败 | operation=%s | key=%s | cause=%s",
            operation, key, cause != null ? cause.getMessage() : "null"),
        cause);
    this.key = key;
    this.operation = operation;
  }

  public Object getKey() {
    return key;
  }

  public String getOperation() {
    return operation;
  }
}
