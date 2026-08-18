package com.njydsz.userinfo.domain.repository;

import java.util.Collection;
import java.util.List;

import com.njydsz.userinfo.infra.entity.RoleDO;

/**
 * 角色 Repository 接口
 *
 * <p>封装角色表（{@code ydsz_role}）的数据访问操作，为 Service 层提供业务语义化的数据访问方法。
 *
 * <p>禁止暴露底层 Mapper，所有数据库操作通过本接口进行。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface RoleRepository {

  /**
   * 根据 ID 查询角色。
   *
   * @param id 角色 ID
   * @return 角色实体，不存在时返回 null
   */
  RoleDO findById(String id);

  /**
   * 根据角色编码查询角色。
   *
   * @param roleCode 角色编码
   * @return 角色实体，不存在时返回 null
   */
  RoleDO findByRoleCode(String roleCode);

  /**
   * 根据 ID 集合批量查询角色。
   *
   * @param ids 角色 ID 集合
   * @return 角色列表
   */
  List<RoleDO> findByIds(Collection<String> ids);

  /**
   * 条件查询角色列表。
   *
   * @param wrapper 查询条件
   * @return 角色列表
   */
  List<RoleDO> list(
      com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<RoleDO> wrapper);

  /**
   * 分页查询角色。
   *
   * @param page 分页参数
   * @param wrapper 查询条件
   * @return 分页结果
   */
  com.baomidou.mybatisplus.extension.plugins.pagination.Page<RoleDO> page(
      com.baomidou.mybatisplus.extension.plugins.pagination.Page<RoleDO> page,
      com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<RoleDO> wrapper);

  /**
   * 保存角色（插入）。
   *
   * @param entity 角色实体
   * @return 插入影响的行数
   */
  int insert(RoleDO entity);

  /**
   * 更新角色。
   *
   * @param entity 角色实体
   * @return 更新影响的行数
   */
  int updateById(RoleDO entity);

  /**
   * 删除角色（逻辑删除）。
   *
   * @param id 角色 ID
   * @return 删除影响的行数
   */
  int deleteById(String id);

  /**
   * 条件删除角色关联数据。
   *
   * @param wrapper 删除条件
   * @return 删除影响的行数
   */
  int delete(
      com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<RoleDO> wrapper);

  /**
   * 统计符合条件的角色数量。
   *
   * @param wrapper 查询条件
   * @return 角色数量
   */
  long count(
      com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<RoleDO> wrapper);
}
