package com.njydsz.userinfo.domain.repository;

import java.util.List;
import java.util.Optional;

import com.njydsz.userinfo.domain.entity.Department;

/**
 * 部门聚合仓储接口。
 *
 * <p>部门是一个独立聚合根，通过 parent_id 自引用构建部门树。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface DepartmentRepository {

  /**
   * 根据 ID 查找部门
   *
   * @param id 部门 ID
   * @return Optional 包装
   */
  Optional<Department> findById(String id);

  /**
   * 根据部门编码查找部门
   *
   * @param deptCode 部门编码
   * @return Optional 包装
   */
  Optional<Department> findByCode(String deptCode);

  /**
   * 查询全部部门（扁平列表，内存中构建树）
   *
   * @return 全部有效部门列表
   */
  List<Department> findAllActive();

  /**
   * 保存部门（新增或更新）
   *
   * @param dept 部门实体
   * @return 保存后的实体
   */
  Department save(Department dept);

  /**
   * 根据 ID 删除部门
   *
   * @param id 部门 ID
   * @return true 表示成功删除
   */
  boolean deleteById(String id);

  /**
   * 查询指定部门的子部门
   *
   * @param parentId 父部门 ID
   * @return 子部门列表
   */
  List<Department> findChildren(String parentId);

  /**
   * 判断部门编码是否已存在
   *
   * @param deptCode 部门编码
   * @return true 表示已存在
   */
  boolean existsByCode(String deptCode);

  /**
   * 判断是否存在子部门
   *
   * @param deptId 部门 ID
   * @return true 表示存在子部门
   */
  boolean hasChildren(String deptId);

  /**
   * 判断部门下是否存在用户
   *
   * @param deptId 部门 ID
   * @return true 表示部门下有用户
   */
  boolean hasUsers(String deptId);
}
