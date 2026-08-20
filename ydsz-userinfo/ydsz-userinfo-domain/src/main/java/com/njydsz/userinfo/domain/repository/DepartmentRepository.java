package com.njydsz.userinfo.domain.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.userinfo.domain.dto.DepartmentDTO;
import com.njydsz.userinfo.domain.query.DepartmentPageQuery;
import com.njydsz.userinfo.domain.vo.DepartmentVO;

/**
 * 部门 Repository 接口
 *
 * <p>封装部门表（{@code ydsz_department}）的数据访问操作，为 Service 层提供业务语义化的数据访问方法。
 *
 * <p>入参为 DTO / Query / 具体字段，返回值为 VO 类型，禁止暴露 MyBatis-Plus 类。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface DepartmentRepository {

  /**
   * 根据 ID 查询部门。
   *
   * @param id 部门 ID
   * @return 部门 VO
   */
  Optional<DepartmentVO> findById(String id);

  /**
   * 根据父级 ID 查询子部门列表。
   *
   * @param parentId 父级部门 ID
   * @return 子部门列表
   */
  List<DepartmentVO> findByParentId(String parentId);

  /**
   * 根据部门编码查询部门。
   *
   * @param deptCode 部门编码
   * @return 部门 VO
   */
  Optional<DepartmentVO> findByDeptCode(String deptCode);

  /**
   * 分页查询部门列表。
   *
   * @param query 分页查询参数
   * @return 分页结果
   */
  PageResponse<List<DepartmentVO>> page(DepartmentPageQuery query);

  /**
   * 条件查询部门列表。
   *
   * @param query 查询参数
   * @return 部门列表
   */
  List<DepartmentVO> list(DepartmentPageQuery query);

  /**
   * 批量根据 ID 查询部门。
   *
   * @param ids 部门 ID 集合
   * @return 部门列表
   */
  List<DepartmentVO> listByIds(Collection<String> ids);

  /**
   * 保存部门（创建或更新）。
   *
   * <p>统一 DTO：创建时 {@code id} 可不传，更新时 {@code id} 必填。
   *
   * @param dto 部门 DTO
   * @return 保存后的部门 VO
   */
  DepartmentVO save(DepartmentDTO dto);

  /**
   * 根据 ID 删除部门（逻辑删除）。
   *
   * @param id 部门 ID
   * @return 是否删除成功
   */
  boolean deleteById(String id);

  /**
   * 根据父级 ID 删除部门。
   *
   * @param parentId 父级部门 ID
   * @return 删除影响的行数
   */
  int deleteByParentId(String parentId);

  /**
   * 统计符合条件的部门数量。
   *
   * @param query 查询参数
   * @return 部门数量
   */
  long countByQuery(DepartmentPageQuery query);
}
