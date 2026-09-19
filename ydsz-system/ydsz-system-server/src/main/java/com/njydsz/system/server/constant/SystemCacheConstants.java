package com.njydsz.system.server.constant;

/**
 * 系统引擎（ydsz-system）专属缓存常量 — 缓存名称与 Key 模板定义。
 *
 * <p>对标原 {@code com.njydsz.common.cache.constant.CacheConstants} 中 {@code SYSTEM_*} 区段， 下沉到本模块以避免
 * ydsz-common-cache 与各业务模块的交叉维护。
 *
 * <p>使用示例：
 *
 * <pre>{@code
 * @Cacheable(
 *     value = SystemCacheConstants.SYSTEM_DICT_ITEM_CACHE,
 *     key = "T(com.njydsz.common.cache.support.CacheKeyBuilder).build('system', 'dict:items', #typeCode)"
 * )
 * public List&lt;DictItemVO&gt; listByTypeCode(String typeCode) { ... }
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.19
 */
public final class SystemCacheConstants {

  private SystemCacheConstants() {
    throw new UnsupportedOperationException("Utility class");
  }

  // ============================== System 模块缓存名 ==============================

  /**
   * 系统字典项缓存。
   *
   * <p><b>key 模板：</b>
   *
   * <ul>
   *   <li>按 typeCode 查列表：{@code ydsz:{tenantId}:system:dict:items:{typeCode}}
   *   <li>按 typeCode+itemCode 查单项：{@code ydsz:{tenantId}:system:dict:item:{typeCode}:{itemCode}}
   * </ul>
   * 当字典项增删改时通过 {@code @CacheEvict} 主动失效。
   */
  public static final String SYSTEM_DICT_ITEM_CACHE = "system:dict:item";

  /**
   * 系统字典类型全量缓存。
   *
   * <p><b>key 模板：</b>{@code ydsz:{tenantId}:system:dict:type:all}。 当字典类型增删改时通过 {@code @CacheEvict}
   * 主动失效。
   */
  public static final String SYSTEM_DICT_TYPE_CACHE = "system:dict:type";

  /**
   * 系统配置缓存。
   *
   * <p><b>key 模板：</b>
   *
   * <ul>
   *   <li>按 configKey 查单值：{@code ydsz:{tenantId}:system:config:value:{configKey}}
   *   <li>按 configGroup 查列表：{@code ydsz:{tenantId}:system:config:group:{configGroup}}
   *   <li>公开配置：{@code ydsz:{tenantId}:system:config:public}}
   * </ul>
   * 当配置变更时通过 {@code @CacheEvict} 主动失效。TTL 默认 30min。
   */
  public static final String SYSTEM_CONFIG_CACHE = "system:config";

  /**
   * 系统变量缓存。
   *
   * <p><b>key 模板：</b>{@code ydsz:{tenantId}:system:variable:{variableKey}}。 当变量变更时通过 {@code @CacheEvict}
   * 主动失效。
   */
  public static final String SYSTEM_VARIABLE_CACHE = "system:variable";
}
