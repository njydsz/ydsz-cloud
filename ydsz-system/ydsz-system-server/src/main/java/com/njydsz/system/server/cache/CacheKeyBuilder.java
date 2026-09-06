package com.njydsz.system.server.cache;

import org.springframework.stereotype.Component;

import com.njydsz.common.cache.support.AbstractModuleCacheKeyBuilder;

/**
 * 缓存键构造器（Spring Cache SpEL 调用入口）。
 *
 * <p>为系统模块的 {@code @Cacheable} / {@code @CacheEvict} SpEL 表达式提供租户感知的缓存键生成能力。
 *
 * <p><b>统一格式（P2-3）：</b>所有 key 均通过 {@code AbstractModuleCacheKeyBuilder.buildKey} 构建，
 * 格式为 {@code ydsz:{tenantId}:{module}:{entity}:{id}}。
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
 * @author ydsz-team
 * @since 26.09.01
 */
@Component("cacheKeyBuilder")
public class CacheKeyBuilder extends AbstractModuleCacheKeyBuilder {

  /** 系统模块标识 */
  private static final String MODULE = "system";

  /**
   * 构造系统模块缓存键构造器。
   */
  public CacheKeyBuilder() {
    super(MODULE);
  }

  // ============================== 系统配置缓存 key ==============================

  /**
   * 生成「按配置键查询」缓存键。
   *
   * @param configKey 配置键
   * @return 格式：{@code ydsz:{tenantId}:system:config:value:{configKey}}
   */
  public String configValue(String configKey) {
    return buildKey("config:value", configKey);
  }

  /**
   * 生成「按配置分组查询」缓存键。
   *
   * @param configGroup 配置分组
   * @return 格式：{@code ydsz:{tenantId}:system:config:group:{configGroup}}
   */
  public String configGroup(String configGroup) {
    return buildKey("config:group", configGroup);
  }

  /**
   * 生成「公开配置查询」缓存键。
   *
   * @return 格式：{@code ydsz:{tenantId}:system:config:public}
   */
  public String configPublic() {
    return buildKey("config:public", "");
  }

  // ============================== 字典项缓存 key ==============================

  /**
   * 生成「按类型+编码查询字典项」缓存键。
   *
   * @param typeCode 字典类型编码
   * @param itemCode 字典项编码
   * @return 格式：{@code ydsz:{tenantId}:system:dict:item:{typeCode}:{itemCode}}
   */
  public String dictItem(String typeCode, String itemCode) {
    return buildKeyPattern("dict:item", typeCode, itemCode);
  }

  /**
   * 生成「按类型查询字典列表」缓存键。
   *
   * @param typeCode 字典类型编码
   * @return 格式：{@code ydsz:{tenantId}:system:dict:items:{typeCode}}
   */
  public String dictList(String typeCode) {
    return buildKey("dict:items", typeCode);
  }

  // ============================== 系统变量缓存 key ==============================

  /**
   * 生成「按变量键查询」缓存键。
   *
   * @param variableKey 变量键
   * @return 格式：{@code ydsz:{tenantId}:system:variable:{variableKey}}
   */
  public String variable(String variableKey) {
    return buildKey("variable", variableKey);
  }
}
