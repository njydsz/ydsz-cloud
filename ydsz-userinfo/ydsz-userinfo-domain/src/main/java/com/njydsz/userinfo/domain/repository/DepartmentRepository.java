package com.njydsz.userinfo.domain.repository;

import java.util.Collection;
import java.util.List;

import com.njydsz.userinfo.infra.entity.DepartmentDO;

/**
 * 部门 Repository 接口
 *
 * <p>封装部门表（{@code ydsz_department}）的数据访问操作，为 Service 层提供业务语义化的数据访问方法。
 *
 * <p>禁止暴露底层 Mapper，所有数据库操作通过本接口进行。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface DepartmentRepository {

  /**
   * 根据 ID 查询部门。
   *
   * @param id 部门 ID
   * @return 部门实体，不存在时返回 null
   */
  DepartmentDO findById(String id);

  /**
   * 根据父级 ID 查询子部门列表。
   *
   * @param parentId 父级部门 ID
   * @return 子部门列表
   */
  List<DepartmentDO> findByParentId(String parentId);

  /**
   * 根据部门编码查询部门。
   *
   * @param deptCode 部门编码
   * @return 部门实体，不存在时返回 null
   */
  DepartmentDO findByDeptCode(String deptCode);

  /**
   * 条件查询部门列表。
   *
   * @param wrapper 查询条件
   * @return 部门列表
   */
  List<DepartmentDO> list(
      com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<DepartmentDO> wrapper);

  /**
   * 批量根据 ID 查询部门。
   *
   * @param ids 部门 ID 集合
   * @return 部门列表
   */
  List<DepartmentDO> listByIds(Collection<String> ids);

  /**
   * 保存部门（插入）。
   *
   * @param entity 部门实体
   * @return 插入影响的行数
   */
  int insert(DepartmentDO entity);

  /**
   * 更新部门。
   *
   * @param entity 部门实体
   * @return 更新影响的行数
   */
  int updateById(DepartmentDO entity);

  /**
   * 删除部门（逻辑删除）。
   *
   * @param id 部门 ID
   * @return 删除影响的行数
   */
  int deleteById(String id);

  /**
   * 条件删除部门。
   *
   * @param wrapper 删除条件
   * @return 删除影响的行数
   */
  int delete(
      com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<DepartmentDO> wrapper);

  /**
   * 统计符合条件的部门数量。
   *
   * @param wrapper 查询条件
   * @return 部门数量
   */
  long count(
      com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<DepartmentDO> wrapper);
}
