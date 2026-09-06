package com.njydsz.common.cache.constant;

/**
 * 缓存名称与 Key 模板常量定义。
 *
 * <p>统一管理各业务模块使用的 Spring Cache 缓存名称（cache name）和缓存键模板，
 * 避免各模块各自硬编码字符串，便于在配置层统一调整 TTL 和缓存策略。
 *
 * <p><b>key 命名约定（P2-3 租户隔离增强）：</b>
 *
 * <ul>
 *   <li>所有缓存 key 必须包含 {@code tenantId} 维度，通过 {@code CacheKeyBuilder} 构建
 *   <li>通用格式：{@code ydsz:{tenantId}:{module}:{entity}:{id}}
 *   <li>示例：{@code ydsz:acme:system:dict:items:user_status}
 * </ul>
 *
 * <p><b>使用示例：</b>
 *
 * <pre>{@code
 * @Cacheable(
 *     value = CacheConstants.SYSTEM_DICT_ITEM_CACHE,
 *     key = "T(com.njydsz.common.cache.support.CacheKeyBuilder).build("system", "dict:items", #dictTypeCode)"
 * )
 * public List&lt;DictItemVO&gt; listByTypeCode(String dictTypeCode) { ... }
 * }</pre>
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
   * <p>当 {@code TenantContextHolder} 中无租户信息时（如定时任务、MQ Consumer），key 中使用此占位符。
   */
  public static final String DEFAULT_TENANT = "default";

  /**
   * 通用缓存 key 分隔符。
   */
  public static final String KEY_SEPARATOR = ":";

  // ============================== 工作流模块缓存 ==============================

  /**
   * 流程定义已发布版本缓存。
   *
   * <p><b>key 模板：</b>{@code ydsz:{tenantId}:flow:def:published:{flowCode}:{version}}。
   * 当流程定义发布/下线/删除时通过 {@code @CacheEvict(allEntries=true)} 失效。
   */
  public static final String FLOW_DEF_PUBLISHED_CACHE = "flow_def_published";

  /**
   * 流程定义最新版本缓存。
   *
   * <p><b>key 模板：</b>{@code ydsz:{tenantId}:flow:def:latest:{flowCode}}。
   * 当流程定义发布新版本时失效。
   */
  public static final String FLOW_DEF_LATEST_CACHE = "flow_def_latest";

  /**
   * 三方审批账号按用户 ID 查询缓存。
   *
   * <p><b>key 模板（P2-3 新增 tenantId）：</b>{@code ydsz:{tenantId}:flow:thirdparty:by_user:{userId}:{platform}}。
   * 当账号绑定/解绑时失效。
   */
  public static final String FLOW_THIRDPARTY_BY_USER_CACHE = "flow_thirdparty_by_user";

  /**
   * 三方审批账号按 OpenID 查询缓存。
   *
   * <p><b>key 模板（P2-3 新增 tenantId）：</b>{@code ydsz:{tenantId}:flow:thirdparty:by_openid:{openId}:{platform}}。
   * 当账号绑定/解绑时失效。
   */
  public static final String FLOW_THIRDPARTY_BY_OPENID_CACHE = "flow_thirdparty_by_openid";

  // ============================== NextWiki 模块缓存 ==============================

  /**
   * NextWiki 文件 ACL 权限缓存。
   *
   * <p><b>key 模板（P2-3 新增 tenantId）：</b>{@code ydsz:{tenantId}:nextwiki:file:acl:{fileNodeId}:{userId}}。
   * 当文件权限变更（分享/配额调整）时通过 {@code @CacheEvict(allEntries=true)} 失效。
   */
  public static final String NEXTWIKI_FILE_ACL_CACHE = "nextwiki:file:acl";

  /**
   * NextWiki 存储配额缓存。
   *
   * <p><b>key 模板（P2-3 新增 tenantId）：</b>{@code ydsz:{tenantId}:nextwiki:quota:{scopeType}:{scopeId}}。
   * 当配额用量变更或配额设置变更时失效。TTL：3 分钟。
   */
  public static final String NEXTWIKI_QUOTA_CACHE = "nextwiki:quota";

  /**
   * NextWiki 文件详情缓存。
   *
   * <p><b>key 模板（P2-3 新增 tenantId）：</b>{@code ydsz:{tenantId}:nextwiki:file:{nodeId}}。
   * TTL：10 分钟。
   */
  public static final String NEXTWIKI_FILE_CACHE = "nextwiki:file";

  /**
   * NextWiki 目录子节点列表缓存。
   *
   * <p><b>key 模板（P2-3 新增 tenantId）：</b>{@code ydsz:{tenantId}:nextwiki:children:{parentId}}。
   * TTL：5 分钟。
   */
  public static final String NEXTWIKI_CHILDREN_CACHE = "nextwiki:children";

  // ============================== System 模块缓存 ==============================

  /**
   * 系统字典项缓存。
   *
   * <p><b>key 模板（P2-3 新增 tenantId）：</b>
   * <ul>
   *   <li>按 typeCode 查列表：{@code ydsz:{tenantId}:system:dict:items:{typeCode}}</li>
   *   <li>按 typeCode+itemCode 查单项：{@code ydsz:{tenantId}:system:dict:item:{typeCode}:{itemCode}}</li>
   * </ul>
   * 当字典项增删改时通过 {@code @CacheEvict} 主动失效。
   */
  public static final String SYSTEM_DICT_ITEM_CACHE = "system:dict:item";

  /**
   * 系统字典类型全量缓存。
   *
   * <p><b>key 模板：</b>{@code ydsz:{tenantId}:system:dict:type:all}。
   * 当字典类型增删改时通过 {@code @CacheEvict} 主动失效。
   */
  public static final String SYSTEM_DICT_TYPE_CACHE = "system:dict:type";

  /**
   * 系统配置缓存。
   *
   * <p><b>key 模板（P2-3 新增 tenantId）：</b>
   * <ul>
   *   <li>按 configKey 查单值：{@code ydsz:{tenantId}:system:config:value:{configKey}}</li>
   *   <li>按 configGroup 查列表：{@code ydsz:{tenantId}:system:config:group:{configGroup}}</li>
   *   <li>公开配置：{@code ydsz:{tenantId}:system:config:public}</li>
   * </ul>
   * 当配置变更时通过 {@code @CacheEvict} 主动失效。TTL 默认 30min。
   */
  public static final String SYSTEM_CONFIG_CACHE = "system:config";

  /**
   * 系统变量缓存。
   *
   * <p><b>key 模板（P2-3 新增 tenantId）：</b>{@code ydsz:{tenantId}:system:variable:{variableKey}}。
   * 当变量变更时通过 {@code @CacheEvict} 主动失效。
   */
  public static final String SYSTEM_VARIABLE_CACHE = "system:variable";
}
