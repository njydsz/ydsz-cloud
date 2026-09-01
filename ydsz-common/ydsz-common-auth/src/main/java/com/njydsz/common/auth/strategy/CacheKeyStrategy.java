package com.njydsz.common.auth.strategy;

import java.util.Set;

/**
 * 缓存 Key 生成策略接口。
 *
 * <p>用于自定义权限缓存 Key 的生成规则，解决默认 {@code tenantId:roleCodes} 格式不可扩展的问题。
 *
 * <p><b>使用示例：</b>
 *
 * <pre>{@code
 * @Component
 * public class TenantAwareCacheKeyStrategy implements CacheKeyStrategy {
 *     @Override
 *     public String generate(String tenantId, String userId, String permission) {
 *         return tenantId + ":" + userId + ":" + permission;
 *     }
 * }
 * }</pre>
 *
 * <p><b>默认实现：</b>{@link DefaultCacheKeyStrategy} 使用 {@code tenantId:roleCodes} 格式。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@FunctionalInterface
public interface CacheKeyStrategy {

  /**
   * 生成缓存 Key。
   *
   * @param tenantId 租户 ID（可为 null）
   * @param roleCodes 角色编码集合
   * @return 缓存 Key
   */
  String generate(String tenantId, Set<String> roleCodes);
}
