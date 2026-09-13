package com.njydsz.common.auth.service;

import com.njydsz.common.auth.model.ColumnScopeInfo;

/**
 * 列权限兜底加载器 SPI。
 *
 * <p>当 Redis 不可用导致 {@link
 * com.njydsz.common.auth.service.impl.LocalRoleColumnPermissionResolver} 无法从 Redis 加载列权限范围时，
 * 通过此接口回退到本地 DB / 配置文件 / 其他数据源加载列权限数据。
 *
 * <p>该接口定义在 ydzz-common-auth 模块中，由业务消费方提供实现并注册为 Spring Bean。
 * 使用 {@code ObjectProvider} 注入，如果 classpath 中无实现则忽略。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see com.njydsz.common.auth.service.impl.LocalRoleColumnPermissionResolver
 */
public interface ColumnScopeFallbackLoader {

  /**
   * 根据角色编码加载列权限范围。
   *
   * <p>用于 Redis 不可用时的本地兜底数据加载。实现方应捕获自身异常并返回空结果，不应抛出运行时异常。
   *
   * @param roleCode 角色编码（如 "admin"、"manager"）
   * @return 该角色的列权限范围；加载失败时返回 {@link ColumnScopeInfo#empty()}，不应返回 {@code null}
   */
  ColumnScopeInfo loadByRoleCode(String roleCode);
}
