package com.njydsz.common.auth.service;

import com.njydsz.common.auth.model.DataScopeInfo;

/**
 * 数据权限兜底加载器 SPI。
 *
 * <p>当 Redis 不可用导致 {@link
 * com.njydsz.common.auth.service.impl.LocalRoleDataPermissionResolver} 无法从 Redis 加载数据权限范围时，
 * 通过此接口回退到本地 DB / 配置文件 / 其他数据源加载基础数据权限数据。
 *
 * <p>该接口定义在 ydzz-common-auth 模块中，由业务消费方提供实现并注册为 Spring Bean。
 * 使用 {@code ObjectProvider} 注入，如果 classpath 中无实现则忽略（此时数据权限链路以缓存内容为限做短期供
 * 给，缓存过期后返回空权限，等同于降级静默）。
 *
 * <p>推荐实现方式（与 {@link
 * com.njydsz.common.auth.service.impl.RedisRoleDataPermissionResolver#resolveByRoles} 的行为对齐）：
 *
 * <ul>
 *   <li>从本地 DB 的 {@code sys_role_data_scope} 表查询角色对应的数据列信息
 *   <li>从本地配置文件加载预定义的静态数据权限模板
 *   <li>返回空权限兜底（不抛异常）
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see com.njydsz.common.auth.service.impl.LocalRoleDataPermissionResolver
 */
public interface DataScopeFallbackLoader {

  /**
   * 根据角色编码加载数据权限范围。
   *
   * <p>用于 Redis 不可用时的本地兜底数据加载。实现方应捕获自身异常并返回空结果，不应抛出运行时异常。
   *
   * @param roleCode 角色编码（如 "admin"、"manager"）
   * @return 该角色的数据权限范围；加载失败时返回 {@link DataScopeInfo#empty()}，不应返回 {@code null}
   */
  DataScopeInfo loadByRoleCode(String roleCode);
}
