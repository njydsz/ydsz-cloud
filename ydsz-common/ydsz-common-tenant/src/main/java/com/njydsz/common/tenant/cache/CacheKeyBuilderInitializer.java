package com.njydsz.common.tenant.cache;

import jakarta.annotation.PostConstruct;

import com.njydsz.common.cache.support.CacheKeyBuilder;
import com.njydsz.common.tenant.TenantContextHolder;

/**
 * 初始化 CacheKeyBuilder 的租户 ID 解析器。
 *
 * <p>将 {@link TenantContextHolder} 注册到 {@link CacheKeyBuilder}，
 * 使缓存键自动包含租户前缀。
 *
 * @author ydsz-team
 * @since 26.09.06
 */
public class CacheKeyBuilderInitializer {

  /**
   * 初始化方法，将租户上下文获取逻辑注册到 CacheKeyBuilder。
   */
  @PostConstruct
  public void init() {
    CacheKeyBuilder.setTenantIdResolver(TenantContextHolder::getTenantId);
  }
}
