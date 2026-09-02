package com.njydsz.system.server.cache;
import org.springframework.stereotype.Component;

import com.njydsz.common.tenant.TenantContextHolder;



/**
 * 缓存键构造器（Spring Cache SpEL 调用入口）。
 *
 * <p>为系统模块的 {@code @Cacheable} / {@code @CacheEvict} SpEL 表达式提供租户感知的缓存键生成能力。
 *
 * <p>使用方式（SpEL）：
 *
 * <pre>{@code
 * @Cacheable(
 *     value = CacheConstants.SYSTEM_CONFIG_CACHE,
 *     key = "@cacheKeyBuilder.configValue(#p0)"
 * )
 * public String getConfigValue(String configKey) { ... }
 * }</pre>
 *
 * <p>所有生成的键均包含租户命名空间前缀，格式 {@code {prefix}:{tenantId}:{key}}。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Component("cacheKeyBuilder")
public final class CacheKeyBuilder {

  private static final String DEFAULT_TENANT = "default";

  private CacheKeyBuilder() {}

  /**
   * 生成「按配置键查询」缓存键。
   *
   * @param configKey 配置键
   * @return 格式：{@code value:{tenantId}:{configKey}}
   */
  public String configValue(String configKey) {
    return "value:" + currentTenant() + ":" + configKey;
  }

  /**
   * 生成「按配置分组查询」缓存键。
   *
   * @param configGroup 配置分组
   * @return 格式：{@code group:{tenantId}:{configGroup}}
   */
  public String configGroup(String configGroup) {
    return "group:" + currentTenant() + ":" + configGroup;
  }

  /**
   * 生成「公开配置查询」缓存键。
   *
   * @return 格式：{@code public:{tenantId}}
   */
  public String configPublic() {
    return "public:" + currentTenant();
  }

  /**
   * 生成「按类型+编码查询字典项」缓存键。
   *
   * @param typeCode 字典类型编码
   * @param itemCode 字典项编码
   * @return 格式：{@code item:{tenantId}:{typeCode}:{itemCode}}
   */
  public String dictItem(String typeCode, String itemCode) {
    return "item:" + currentTenant() + ":" + typeCode + ":" + itemCode;
  }

  /**
   * 生成「按类型查询字典列表」缓存键。
   *
   * @param typeCode 字典类型编码
   * @return 格式：{@code list:{tenantId}:{typeCode}}
   */
  public String dictList(String typeCode) {
    return "list:" + currentTenant() + ":" + typeCode;
  }

  /**
   * 生成「按变量键查询」缓存键。
   *
   * @param variableKey 变量键
   * @return 格式：{@code {tenantId}:{variableKey}}
   */
  public String variable(String variableKey) {
    return currentTenant() + ":" + variableKey;
  }

  private static String currentTenant() {
    String tenantId = TenantContextHolder.getTenantId();
    return tenantId != null && !tenantId.isBlank() ? tenantId : DEFAULT_TENANT;
  }
}
