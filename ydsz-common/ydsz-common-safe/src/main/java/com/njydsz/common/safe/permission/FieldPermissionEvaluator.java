package com.njydsz.common.safe.permission;

import java.util.Set;

/**
 * 字段级权限求值器 SPI。
 *
 * <p>业务模块（如 ydsz-common-auth）提供实现类接入字段权限校验框架，
 * 通过 Spring 容器注入 {@link FieldPermissionAspectImpl}。
 *
 * <p><b>职责：</b>
 *
 * <ul>
 *   <li>根据用户 ID 返回当前被授权的字段标识集合</li>
 *   <li>判断用户是否拥有超级管理员权限（超级管理员跳过字段校验）</li>
 *   <li>当字段权限尚未配置时返回空集（安全默认：不可访问）</li>
 * </ul>
 *
 * <p><b>实现示例：</b>
 *
 * <pre>{@code
 * @Component
 * public class AuthFieldPermissionEvaluator implements FieldPermissionEvaluator {
 *   @Override
 *   public Set<String> getAuthorizedFields(String userId, String tenantId) {
 *       return redisOps.smembers("field:perm:" + userId);
 *   }
 *   @Override
 *   public boolean isSuperAdmin(String userId) {
 *       return authService.hasAllPermissions(userId);
 *   }
 * }
 * }</pre>
 *
 * @author ydsz
 * @since 26.10.14
 * @see FieldPermissionAspectImpl
 */
public interface FieldPermissionEvaluator {

  /**
   * 获取用户被授权的字段权限集合。
   *
   * @param userId 当前用户 ID
   * @param tenantId 当前租户 ID
   * @return 字段标识集合，返回 null 表示查询失败（降级为空集）
   */
  Set<String> getAuthorizedFields(String userId, String tenantId);

  /**
   * 判断用户是否拥有超级管理员权限。
   *
   * <p>超级管理员直接跳过字段级权限校验。
   *
   * @param userId 当前用户 ID
   * @return true 表示超级管理员
   */
  default boolean isSuperAdmin(String userId) {
    return false;
  }
}
