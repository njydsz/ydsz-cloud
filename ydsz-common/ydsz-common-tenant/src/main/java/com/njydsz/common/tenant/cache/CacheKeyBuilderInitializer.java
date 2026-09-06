package com.njydsz.common.tenant.cache;

import jakarta.annotation.PostConstruct;

import com.njydsz.common.cache.support.CacheKeyBuilder;
import com.njydsz.common.cache.support.TenantIdResolver;
import com.njydsz.common.tenant.TenantContextHolder;

/**
 * 初始化 CacheKeyBuilder 的租户 ID 解析器。
 *
 * <p>将 {@link TenantContextHolder} 注册到 {@link CacheKeyBuilder}，
 * 使缓存键自动包含租户前缀（格式：{@code ydsz:{tenantId}:{module}:{entity}:{id}}）。
 *
 * <p>该初始化器在 common-tenant 模块中生效 — 当且仅当 common-tenant 模块被引入时，
 * 缓存 key 自动具备租户隔离维度。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public class CacheKeyBuilderInitializer {

  /**
   * 初始化方法，将租户上下文获取逻辑以 {@link TenantIdResolver} 形式注册到 {@link CacheKeyBuilder}。
   *
   * <p>该方法通过的方法引用方式注入，将 {@link TenantContextHolder#getTenantId()} 的 result 映射到 resolver SPI。
   */
  @PostConstruct
  public void init() {
    TenantIdResolver resolver = TenantContextHolder::getTenantId;
    CacheKeyBuilder.setTenantIdResolver(resolver);
  }
}
