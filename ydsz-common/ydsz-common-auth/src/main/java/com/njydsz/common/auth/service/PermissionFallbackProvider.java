package com.njydsz.common.auth.service;

import java.util.Set;

/**
 * 权限降级提供者 SPI。
 *
 * <p>当 Redis 不可用且本地缓存未命中时，{@link RbacPermissionEvaluator} 通过此接口回退到 DB 加载基础权限数据。
 *
 * <p>该接口定义在 ydzsz-common-auth 模块中，由 ydzsz-system 模块（或业务消费方）提供实现并注册为 Spring Bean。
 * 使用 {@code @Autowired(required = false)} 注入，如果 classpath 中无实现则忽略。
 *
 * <p>简化方案：实现方从 {@code sys_api_permission} 表中查询已注册的 API 权限码，
 * 给超级管理员角色返回所有权限，其他角色返回空集合。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see RbacPermissionEvaluator
 */
public interface PermissionFallbackProvider {

  /**
   * 根据角色编码和租户 ID 查询该角色拥有的 API 权限码集合。
   *
   * @param roleCode 角色编码（如 "admin"、"super_admin"）
   * @param tenantId 租户 ID（可为 null，表示默认租户）
   * @return 该角色的 API 权限码集合；无数据时返回空集合，不应返回 null
   */
  Set<String> getRoleApiCodes(String roleCode, String tenantId);
}
