package com.njydsz.common.cache.support;

/**
 * 租户 ID 解析器 SPI — 由宿主模块实现并注入到 {@link CacheKeyBuilder}。
 *
 * <p>该接口将缓存 key 构造逻辑与租户上下文解耦，使 common-cache 无需依赖 common-tenant。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@FunctionalInterface
public interface TenantIdResolver {

  /**
   * 获取当前线程上下文中的租户 ID。
   *
   * @return 当前租户 ID；无上下文时返回 {@code null}
   */
  String getCurrentTenantId();
}
