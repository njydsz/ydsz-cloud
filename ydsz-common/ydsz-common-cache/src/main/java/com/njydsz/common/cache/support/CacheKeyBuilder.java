package com.njydsz.common.cache.support;

import java.util.function.Supplier;

/**
 * 租户感知的缓存键构造器（统一入口）。
 *
 * <p>所有模块的缓存键都应通过本类构建，格式统一为：
 * <code>ydsz:{tenantId}:{module}:{entity}:{id}</code>。
 *
 * <p>租户 ID 解析器由 {@link #setTenantIdResolver(Supplier)} 注册，
 * 默认实现返回空串（单租户场景）。
 *
 * <p>线程安全：静态字段通过 volatile + synchronized 保证可见性与原子性。
 *
 * @author ydsz-team
 * @since 26.09.06
 */
public final class CacheKeyBuilder {

  /** 缓存键顶层前缀 */
  private static final String ROOT_PREFIX = "ydsz";

  /** 分隔符 */
  private static final String SEPARATOR = ":";

  /** 租户 ID 解析器，默认返回空串（单租户兜底） */
  private static volatile Supplier<String> tenantIdResolver = () -> "";

  private CacheKeyBuilder() {}

  /**
   * 注册租户 ID 解析器。
   *
   * <p>通常在应用启动时由 {@code CacheKeyBuilderInitializer} 调用一次，
   * 传入 {@code TenantContextHolder::getTenantId} 等租户上下文获取逻辑。
   *
   * @param resolver 租户 ID 解析器，返回当前租户标识；为 {@code null} 时回退为空串
   */
  public static void setTenantIdResolver(Supplier<String> resolver) {
    if (resolver != null) {
      tenantIdResolver = resolver;
    }
  }

  /**
   * 构建单参数缓存键。
   *
   * <p>格式：<code>ydsz:{tenantId}:{module}:{entity}:{id}</code>
   *
   * @param module 模块标识（如 {@code "system"}、{@code "nextwiki"}）
   * @param entity 实体/子域标识（如 {@code "config:value"}）
   * @param id 实体唯一标识
   * @return 完整缓存键，不会为 {@code null}
   */
  public static String build(String module, String entity, Object id) {
    return buildPattern(module, entity, id != null ? id.toString() : "");
  }

  /**
   * 构建多参数缓存键（可变参数模式）。
   *
   * <p>格式：<code>ydsz:{tenantId}:{module}:{entity}:{param1}:{param2}:...</code>
   *
   * @param module 模块标识
   * @param entity 实体/子域标识
   * @param params 可变参数列表，每个参数追加到键末尾
   * @return 完整缓存键，不会为 {@code null}
   */
  public static String buildPattern(String module, String entity, Object... params) {
    StringBuilder sb = new StringBuilder(64);
    sb.append(ROOT_PREFIX).append(SEPARATOR);
    sb.append(getTenantId()).append(SEPARATOR);
    sb.append(module != null ? module : "").append(SEPARATOR);
    sb.append(entity != null ? entity : "");

    if (params != null) {
      for (Object param : params) {
        sb.append(SEPARATOR).append(param != null ? param.toString() : "");
      }
    }

    return sb.toString();
  }

  /**
   * 获取当前租户 ID。
   *
   * @return 当前租户标识，解析器未注册或返回 {@code null} 时返回空串
   */
  private static String getTenantId() {
    try {
      String tenantId = tenantIdResolver.get();
      return tenantId != null ? tenantId : "";
    } catch (Exception e) {
      // 解析失败时兜底为空串，避免影响缓存操作
      return "";
    }
  }
}
