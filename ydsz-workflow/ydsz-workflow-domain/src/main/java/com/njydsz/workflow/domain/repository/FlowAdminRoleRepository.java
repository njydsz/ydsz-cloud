package com.njydsz.workflow.domain.repository;

import java.util.List;
import java.util.Optional;

import com.njydsz.workflow.domain.vo.FlowAdminRoleVO;


/**
 * 管理员角色仓储接口（domain 层契约）。
 *
 * <p>定义管理员角色（ydsz_flow_admin_role）的持久化抽象，隔离领域模型与具体数据访问技术实现。
 * 应用层 Service 通过此接口操作管理员角色聚合，不直接依赖 MyBatis Mapper。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>以领域语义方法暴露数据访问能力，禁止 Mapper 透传
 *   <li>返回领域 VO（{@link FlowAdminRoleVO}），非 DTO / infra 实体
 *   <li>查询入参使用具体字段（userId / roleCode / tenantId 等）
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public interface FlowAdminRoleRepository {

  /**
   * 保存管理员角色（新增）。
   *
   * @param vo 管理员角色 VO
   * @return 保存后的管理员角色 VO（含生成的 id 与审计字段）
   */
  FlowAdminRoleVO save(FlowAdminRoleVO vo);

  /**
   * 根据 ID 查询管理员角色。
   *
   * @param id 管理员角色 ID
   * @return 管理员角色 VO；不存在返回 {@code Optional.empty()}
   */
  Optional<FlowAdminRoleVO> findById(String id);

  /**
   * 根据用户 ID 查询管理员角色列表。
   *
   * @param userId 用户 ID
   * @return 管理员角色 VO 列表
   */
  List<FlowAdminRoleVO> findByUserId(String userId);

  /**
   * 根据角色编码查询管理员角色列表。
   *
   * @param roleCode 角色编码
   * @return 管理员角色 VO 列表
   */
  List<FlowAdminRoleVO> findByRoleCode(String roleCode);

  /**
   * 根据 ID 删除管理员角色。
   *
   * @param id 管理员角色 ID
   */
  void deleteById(String id);

  /**
   * 更新管理员角色。
   *
   * @param vo 管理员角色 VO（含 id）
   * @return 更新后的管理员角色 VO
   */
  FlowAdminRoleVO update(FlowAdminRoleVO vo);

  /**
   * 按用户 ID + 角色编码查询管理员角色。
   *
   * <p>用于权限校验场景：判断指定用户是否拥有指定角色。
   *
   * @param userId 用户 ID
   * @param roleCode 角色编码
   * @return 管理员角色 VO；不存在返回 {@code Optional.empty()}
   */
  Optional<FlowAdminRoleVO> findByUserAndRole(String userId, String roleCode);

  /**
   * 按用户 ID + 角色编码 + 租户 ID 查询管理员角色。
   *
   * <p>用于权限校验场景：判断指定用户是否拥有指定角色（带租户隔离）。
   *
   * @param userId 用户 ID
   * @param roleCode 角色编码
   * @param tenantId 租户 ID
   * @return 管理员角色 VO；不存在返回 {@code Optional.empty()}
   */
  Optional<FlowAdminRoleVO> findByUserAndRole(String userId, String roleCode, String tenantId);

  /**
   * 按用户 ID 查询管理员角色列表（带租户隔离）。
   *
   * @param userId 用户 ID
   * @param tenantId 租户 ID
   * @return 管理员角色 VO 列表
   */
  List<FlowAdminRoleVO> findByUserId(String userId, String tenantId);
}
