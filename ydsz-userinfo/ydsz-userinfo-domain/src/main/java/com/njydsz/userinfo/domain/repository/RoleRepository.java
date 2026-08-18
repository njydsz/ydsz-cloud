package com.njydsz.userinfo.domain.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.userinfo.domain.dto.RoleCreateDTO;
import com.njydsz.userinfo.domain.dto.RoleUpdateDTO;
import com.njydsz.userinfo.domain.query.RolePageQuery;
import com.njydsz.userinfo.domain.vo.RoleVO;

/**
 * 角色 Repository 接口
 *
 * <p>封装角色表（{@code ydsz_role}）的数据访问操作，为 Service 层提供业务语义化的数据访问方法。
 *
 * <p>入参为 DTO / Query / 具体字段，返回值为 VO 类型，禁止暴露 MyBatis-Plus 类。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface RoleRepository {

  /**
   * 根据 ID 查询角色。
   *
   * @param id 角色 ID
   * @return 角色 VO
   */
  Optional<RoleVO> findById(String id);

  /**
   * 根据角色编码查询角色。
   *
   * @param roleCode 角色编码
   * @return 角色 VO
   */
  Optional<RoleVO> findByRoleCode(String roleCode);

  /**
   * 根据 ID 集合批量查询角色。
   *
   * @param ids 角色 ID 集合
   * @return 角色列表
   */
  List<RoleVO> findByIds(Collection<String> ids);

  /** 根据角色ID列表批量查询 */
  List<RoleVO> listByIds(Collection<String> ids);

  /**
   * 分页查询角色列表。
   *
   * @param query 分页查询参数
   * @return 分页结果
   */
  PageResponse<List<RoleVO>> page(RolePageQuery query);

  /**
   * 条件查询角色列表。
   *
   * @param query 查询参数
   * @return 角色列表
   */
  List<RoleVO> list(RolePageQuery query);

  /**
   * 创建角色。
   *
   * @param dto 创建 DTO
   * @return 创建后的角色 VO
   */
  RoleVO create(RoleCreateDTO dto);

  /**
   * 更新角色。
   *
   * @param dto 更新 DTO
   * @return 更新后的角色 VO
   */
  RoleVO update(RoleUpdateDTO dto);

  /**
   * 根据 ID 删除角色（逻辑删除）。
   *
   * @param id 角色 ID
   * @return 是否删除成功
   */
  boolean deleteById(String id);

  /**
   * 统计符合条件的角色数量。
   *
   * @param query 查询参数
   * @return 角色数量
   */
  long countByQuery(RolePageQuery query);
}
