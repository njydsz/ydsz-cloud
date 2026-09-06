package com.njydsz.system.domain.repository;

import java.util.List;
import java.util.Optional;

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.system.domain.dto.ApiPermissionDTO;
import com.njydsz.system.domain.query.ApiPermissionQuery;
import com.njydsz.system.domain.vo.ApiPermissionVO;

/**
 * 接口权限仓储接口（domain 层契约）。
 *
 * <p>定义接口权限域的数据访问能力，Infra 层负责实现。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>以领域语义方法暴露数据访问能力，禁止 Mapper 透传
 *   <li>返回领域 VO（{@link ApiPermissionVO}），非 DTO / infra 实体
 *   <li>CUD 入参使用领域 DTO（{@link ApiPermissionDTO}），禁止接受 infra 实体
 *   <li>分页查询入参使用领域 Query（{@link ApiPermissionQuery}）
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public interface ApiPermissionRepository {

  /**
   * 分页查询接口权限。
   *
   * @param query 分页查询参数
   * @return 分页结果（VO 分页）
   */
  PageResponse<List<ApiPermissionVO>> findByPage(ApiPermissionQuery query);

  /**
   * 按 ID 查询接口权限。
   *
   * @param id 主键 ID
   * @return 接口权限 VO；不存在返回 {@code Optional.empty()}
   */
  Optional<ApiPermissionVO> findById(String id);

  /**
   * 按租户和权限码查询接口权限。
   *
   * @param tenantId 租户 ID
   * @param apiCode  权限码
   * @return 接口权限 VO；不存在返回 {@code Optional.empty()}
   */
  Optional<ApiPermissionVO> findByTenantAndApiCode(String tenantId, String apiCode);

  /**
   * 查询租户下全部接口权限。
   *
   * @param tenantId 租户 ID
   * @return 接口权限 VO 列表
   */
  List<ApiPermissionVO> listAllByTenant(String tenantId);

  /**
   * 插入接口权限。
   *
   * @param dto 接口权限 DTO
   * @return 插入成功返回 {@code true}
   */
  boolean insert(ApiPermissionDTO dto);

  /**
   * 更新接口权限。
   *
   * @param dto 接口权限 DTO（含 id）
   * @return 更新成功返回 {@code true}
   */
  boolean updateById(ApiPermissionDTO dto);

  /**
   * 逻辑删除接口权限。
   *
   * @param id 主键 ID
   * @return 删除成功返回 {@code true}
   */
  boolean removeById(String id);

  /**
   * 批量插入接口权限（跳过已有权限码）。
   *
   * @param dtos 接口权限 DTO 列表
   * @return 实际插入的记录数
   */
  int insertBatchSkipExisting(List<ApiPermissionDTO> dtos);
}
