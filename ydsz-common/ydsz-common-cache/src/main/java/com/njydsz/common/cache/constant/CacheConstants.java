package com.njydsz.common.cache.constant;

/**
 * 缓存 Key 基础常量定义。
 *
 * <p>仅保留跨模块共享的基础设施常量（前缀、分隔符、默认租户）。
 * 各业务模块的缓存名称常量已分拆至各自模块：
 *
 * <ul>
 *   <li>工作流 → {@code WorkflowCacheConstants}
 *   <li>文件引擎 → {@code NextwikiCacheConstants}
 *   <li>系统引擎 → {@code SystemCacheConstants}
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public final class CacheConstants {

  private CacheConstants() {
    throw new UnsupportedOperationException("Utility class");
  }

  // ============================== Key 模板格式常量 ==============================

  /**
   * 全局缓存 key 前缀。
   *
   * <p>所有通过 {@link com.njydsz.common.cache.support.CacheKeyBuilder} 构建的 key 统一以此为前缀。
   */
  public static final String KEY_PREFIX = "ydsz";

  /**
   * 无租户上下文时的默认占位符。
   *
   * <p>当租户上下文中无租户信息时（如定时任务、MQ Consumer），key 中使用此占位符。
   */
  public static final String DEFAULT_TENANT = "default";

  /**
   * 通用缓存 key 分隔符。
   */
  public static final String KEY_SEPARATOR = ":";
}
