package com.njydsz.common.cache.support;

/**
 * 租户感知缓存键构造器 — 全局唯一的缓存键生成入口。
 *
 * <p>所有模块的缓存 key 必须通过此类构建，统一格式为 {@code ydsz:{tenantId}:{module}:{entity}:{id}}。
 *
 * <p>通过 {@link TenantIdResolver} SPI 解耦租户上下文获取逻辑（由
 * com.njydsz.common.tenant.cache.CacheKeyBuilderInitializer 在启动时注入）。
 *
 * <p><b>命名约定：</b>
 *
 * <ul>
 *   <li>{@code build(module, entity, id)} → {@code ydsz:{tenantId}:{module}:{entity}:{id}}
 *   <li>{@code buildPattern(segments...)} → {@code ydsz:{tenantId}:{segment1}:{segment2}:...}
 *   <li>无租户上下文时回退到 {@code "default"} 前缀
 * </ul>
 *
 * <p><b>使用示例：</b>
 *
 * <pre>{@code
 * // 1. 标准三参数形式
 * String key = CacheKeyBuilder.build("system", "dict:items", dictTypeCode);
 * // 结果（有租户）：ydsz:acme:system:dict:items:user_status
 * // 结果（无租户）：ydsz:default:system:dict:items:user_status
 *
 * // 2. 自由拼接形式（适配复杂 key）
 * String key = CacheKeyBuilder.buildPattern("config", "value", configKey);
 * // 结果：ydsz:acme:config:value:some.key
 *
 * // 3. 带前缀模块标识（推荐）
 * String key = CacheKeyBuilder.build("nextwiki", "quota", scopeType + "/" + scopeId);
 * // 结果：ydsz:acme:nextwiki:quota:user/123456
 * }</pre>
 *
 * <p><b>多租户隔离升级说明（P2-3）：</b>
 *
 * <ul>
 *   <li>Web 请求：由 CacheKeyBuilderInitializer 注入 TenantIdResolver
 *   <li>定时任务 / MQ Consumer：resolver 返回 null → 回退到 {@code "default"}
 * </ul>
 *
 * <p><b>与 Spring Cache SpEL 互操作：</b>
 *
 * <p>如需在 {@code @Cacheable} SpEL 表达式中使用，可在各自模块内注册为 Spring Bean：
 *
 * <pre>{@code
 * @Component
 * public class ModuleCacheKeyBuilder {
 *     public String dictItems(String typeCode) {
 *         return CacheKeyBuilder.build("system", "dict:items", typeCode);
 *     }
 * }
 * }</pre>
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

  /** 租户 ID 解析器（由 CacheKeyBuilderInitializer 注入） */
  private static volatile TenantIdResolver tenantIdResolver = null;

  private CacheKeyBuilder() {
    throw new UnsupportedOperationException("Utility class");
  }

  // ============================== SPI 注入 ==============================

  /**
   * 注册租户 ID 解析器（由 com.njydsz.common.tenant.cache.CacheKeyBuilderInitializer 调用）。
   *
   * <p>启动时调用一次，将 TenantContextHolder::getTenantId 注入。
   *
   * @param resolver 租户 ID 解析器
   */
  public static void setTenantIdResolver(TenantIdResolver resolver) {
    tenantIdResolver = resolver;
  }

  /**
   * 清除租户 ID 解析器（用于测试）。
   */
  public static void clearTenantIdResolver() {
    tenantIdResolver = null;
  }

  // ============================== 标准 build 方法 ==============================

  /**
   * 构建租户隔离缓存键。
   *
   * <p>格式：{@code ydsz:{tenantId}:{module}:{entity}:{id}}
   *
   * @param module 模块名（如 {@code "system"}、{@code "nextwiki"}）
   * @param entity 实体名
   * @param id 实体标识
   * @return 租户隔离的缓存键
   */
  public static String build(String module, String entity, String id) {
    return buildPattern(currentTenant(), module, entity, id);
  }

  /**
   * 构建不带租户前缀的全局缓存键（仅供真正全局共享的场景使用）。
   *
   * <p>格式：{@code ydsz:{module}:{entity}:{id}}。
   *
   * <p><b>慎用：</b>仅在确认数据为全局共享（不区分租户）时使用。
   *
   * @param module 模块名
   * @param entity 实体名
   * @param id 实体标识
   * @return 不带租户的缓存键
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
   * @param segments key 段序列（至少一段）
   * @return 拼接后的缓存键
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
   * 构建通配符扫描模式（用于 SCAN 命令，仅后台管理工具使用）。
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
   * 获取当前租户 ID（从已注入的 resolver 派生）。
   *
   * <p>无租户上下文时回退到 {@link #DEFAULT_TENANT_FILLER}。
   *
   * @return 当前租户 ID 或 {@code "default"}
   */
  public static String currentTenantId() {
    return currentTenant();
  }

  // ============================== 内部工具方法 ==============================

  private static String currentTenant() {
    TenantIdResolver resolver = tenantIdResolver;
    if (resolver != null) {
      String tenantId = resolver.getCurrentTenantId();
      if (tenantId != null && !tenantId.isBlank()) {
        return tenantId;
      }
    }
    return DEFAULT_TENANT;
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
