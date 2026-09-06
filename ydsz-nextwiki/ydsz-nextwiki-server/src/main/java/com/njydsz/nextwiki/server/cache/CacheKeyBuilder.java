package com.njydsz.nextwiki.server.cache;

import org.springframework.stereotype.Component;

import com.njydsz.common.cache.support.AbstractModuleCacheKeyBuilder;

/**
 * NextWiki 缓存键构造器（Spring Cache SpEL 调用入口）。
 *
 * <p>为 NextWiki 模块的 {@code @Cacheable} / {@code @CacheEvict} SpEL 表达式提供租户感知的缓存键生成能力。
 *
 * <p>统一格式：{@code ydsz:{tenantId}:nextwiki:{entity}:{id}}。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Component("nextwikiCacheKeyBuilder")
public class CacheKeyBuilder extends AbstractModuleCacheKeyBuilder {

  /** NextWiki 模块标识 */
  private static final String MODULE = "nextwiki";

  /**
   * 构造 NextWiki 模块缓存键构造器。
   */
  public CacheKeyBuilder() {
    super(MODULE);
  }

  /**
   * 生成文件 ACL 缓存键。
   *
   * @param fileNodeId 文件节点 ID
   * @param userId 用户 ID
   * @return 格式：{@code ydsz:{tenantId}:nextwiki:file:acl:{fileNodeId}:{userId}}
   */
  public String fileAcl(String fileNodeId, String userId) {
    return buildKeyPattern("file:acl", fileNodeId, userId);
  }
}
