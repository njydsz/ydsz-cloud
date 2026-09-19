package com.njydsz.nextwiki.server.constant;

/**
 * 文件引擎（ydsz-nextwiki）专属缓存常量 — 缓存名称与 Key 模板定义。
 *
 * <p>对标原 {@code com.njydsz.common.cache.constant.CacheConstants} 中 {@code NEXTWIKI_*} 区段， 下沉到本模块以避免
 * ydsz-common-cache 与各业务模块的交叉维护。
 *
 * @author ydsz-team
 * @since 26.09.19
 */
public final class NextwikiCacheConstants {

  private NextwikiCacheConstants() {
    throw new UnsupportedOperationException("Utility class");
  }

  // ============================== NextWiki 模块缓存名 ==============================

  /**
   * NextWiki 文件 ACL 权限缓存。
   *
   * <p><b>key 模板：</b>{@code ydsz:{tenantId}:nextwiki:file:acl:{fileNodeId}:{userId}}。
   * 当文件权限变更（分享/配额调整）时通过 {@code @CacheEvict(allEntries=true)} 失效。
   */
  public static final String NEXTWIKI_FILE_ACL_CACHE = "nextwiki:file:acl";

  /**
   * NextWiki 存储配额缓存。
   *
   * <p><b>key 模板：</b>{@code ydsz:{tenantId}:nextwiki:quota:{scopeType}:{scopeId}}。
   * 当配额用量变更或配额设置变更时失效。TTL：3 分钟。
   */
  public static final String NEXTWIKI_QUOTA_CACHE = "nextwiki:quota";

  /**
   * NextWiki 文件详情缓存。
   *
   * <p><b>key 模板：</b>{@code ydsz:{tenantId}:nextwiki:file:{nodeId}}。 TTL：10 分钟。
   */
  public static final String NEXTWIKI_FILE_CACHE = "nextwiki:file";

  /**
   * NextWiki 目录子节点列表缓存。
   *
   * <p><b>key 模板：</b>{@code ydsz:{tenantId}:nextwiki:children:{parentId}}。 TTL：5 分钟。
   */
  public static final String NEXTWIKI_CHILDREN_CACHE = "nextwiki:children";
}
