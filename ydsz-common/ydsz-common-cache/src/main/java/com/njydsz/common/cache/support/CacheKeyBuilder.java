package com.njydsz.common.cache.support;

import java.util.function.Supplier;

/**
 * 租户感知缓存键构造器 — 全局唯一的缓存键生成入口。
 *
 * <p>所有模块的缓存 key 必须通过此类构建，统一格式为 {@code ydsz:{tenantId}:{module}:{entity}:{id}}。
 *
 * <p><b>命名约定：</b>
 *
 * <ul>
 *   <li>{@code build(module, entity, id)} → {@code ydsz:{tenantId}:{module}:{entity}:{id}}
 *   <li>{@code buildPattern(segments...)} → {@code ydsz:{tenantId}:{segment1}:{segment2}:...}
 *   <li>无租户上下文时回退到 {@code "default"} 前缀
 * </ul>
 *
 * <p><b>租户隔离策略：</b>
 *
 * <ul>
 *   <li>通过 {@link #setTenantIdResolver(Supplier)} 设置租户 ID 解析器
 *   <li>未设置时回退到 {@code "default"}
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public final class CacheKeyBuilder {

  /** 全局缓存 key 前缀 */
  private static final String GLOBAL_PREFIX = "ydsz";

  /** 无租户上下文时的回退值 */
  private static final String DEFAULT_TENANT = "default";

  /** 分隔符 */
  private static final String SEPARATOR = ":";

  /** 租户 ID 解析器（由上层模块初始化） */
  private static Supplier<String> tenantIdResolver = () -> DEFAULT_TENANT;

  private CacheKeyBuilder() {
    throw new UnsupportedOperationException("Utility class");
  }

  /**
   * 设置租户 ID 解析器。
   *
   * <p>建议在应用启动时调用，传入租户上下文获取逻辑。
   *
   * @param resolver 租户 ID 解析器
   */
  public static void setTenantIdResolver(Supplier<String> resolver) {
    if (resolver != null) {
      tenantIdResolver = resolver;
    }
  }

  // ============================== 标准 build 方法 ==============================

  /**
   * 构建租户隔离缓存键。
   *
   * <p>格式：{@code ydsz:{tenantId}:{module}:{entity}:{id}}
   *
   * @param module 模块名（如 {@code "system"}、{@code "nextwiki"}、{@code "workflow"}）
   * @param entity 实体名（如 {@code "dict:items"}、{@code "config:value"}）
   * @param id 实体标识（如 dictTypeCode、configKey、userId 等）
   * @return 租户隔离的缓存键，如 {@code ydsz:acme:system:dict:items:user_status}
   */
  public static String build(String module, String entity, String id) {
    return buildPattern(currentTenant(), module, entity, id);
  }

  /**
   * 构建不带租户前缀的全局缓存键（仅供真正全局共享的场景使用）。
   *
   * <p>格式：{@code ydsz:{module}:{entity}:{id}}。
   *
   * <p><b>慎用：</b>仅在确认数据为全局共享（不区分租户）时使用。误用会导致租户数据串扰。
   *
   * @param module 模块名
   * @param entity 实体名
   * @param id 实体标识
   * @return 不带租户的缓存键，如 {@code ydsz:system:config:public}
   */
  public static String buildWithoutTenant(String module, String entity, String id) {
    return join(GLOBAL_PREFIX, module, entity, nullOrDefault(id));
  }

  // ============================== 自由拼接方法 ==============================

  /**
   * 自由拼接缓存键段（前缀自动注入租户 ID）。
   *
   * <p>格式：{@code ydsz:{tenantId}:{segment1}:{segment2}:...}
   *
   * <p>适用于 key 段不固定或需要动态组合的场景。
   *
   * @param segments key 段序列（至少一段）
   * @return 拼接后的缓存键，如 {@code ydsz:acme:nextwiki:quota:user:123456}
   * @throws IllegalArgumentException segments 为空时抛出
   */
  public static String buildPattern(String... segments) {
    if (segments == null || segments.length == 0) {
      throw new IllegalArgumentException("Cache key segments must not be empty");
    }
    StringBuilder sb = new StringBuilder();
    sb.append(GLOBAL_PREFIX).append(SEPARATOR).append(currentTenant());
    for (String segment : segments) {
      sb.append(SEPARATOR).append(nullOrDefault(segment));
    }
    return sb.toString();
  }

  /**
   * 构建通配符扫描模式（用于 SCAN / KEYS 命令）。
   *
   * <p>格式：{@code ydsz:{tenantId}:{pattern}*}
   *
   * <p><b>注意：</b>仅用于后台管理工具的缓存清理，生产环境禁止使用 KEYS 命令。
   *
   * @param pattern 待匹配的模式（可包含 {@code *} 通配符）
   * @return 通配符模式字符串
   */
  public static String buildScanPattern(String pattern) {
    return GLOBAL_PREFIX + SEPARATOR + currentTenant() + SEPARATOR + nullOrDefault(pattern) + "*";
  }

  /**
   * 构建指定租户的扫描模式（供跨租户管理操作使用）。
   *
   * @param tenantId 目标租户 ID（为空则使用当前租户）
   * @param pattern 待匹配的模式
   * @return 通配符模式字符串
   */
  public static String buildScanPatternForTenant(String tenantId, String pattern) {
    String tid = tenantId != null && !tenantId.isBlank() ? tenantId : currentTenant();
    return GLOBAL_PREFIX + SEPARATOR + tid + SEPARATOR + nullOrDefault(pattern) + "*";
  }

  // ============================== 租户 ID 获取 ==============================

  /**
   * 获取当前租户 ID（从线程上下文派生）。
   *
   * <p>无租户上下文时回退到 {@value #DEFAULT_TENANT}。
   *
   * @return 当前租户 ID 或 {@value #DEFAULT_TENANT}
   */
  public static String currentTenantId() {
    return currentTenant();
  }

  // ============================== 内部工具方法 ==============================

  private static String currentTenant() {
    try {
      String tenantId = tenantIdResolver.get();
      return tenantId != null && !tenantId.isBlank() ? tenantId : DEFAULT_TENANT;
    } catch (Exception e) {
      return DEFAULT_TENANT;
    }
  }

  private static String nullOrDefault(String value) {
    return value != null ? value : DEFAULT_TENANT;
  }

  private static String join(String... parts) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < parts.length; i++) {
      if (i > 0) {
        sb.append(SEPARATOR);
      }
      sb.append(parts[i] != null ? parts[i] : DEFAULT_TENANT);
    }
    return sb.toString();
  }
}
