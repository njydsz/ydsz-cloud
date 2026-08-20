package com.njydsz.userinfo.domain.repository;

import java.util.List;
import java.util.Optional;

import com.njydsz.userinfo.domain.dto.AuthPolicyCreateDTO;
import com.njydsz.userinfo.domain.dto.AuthPolicyUpdateDTO;
import com.njydsz.userinfo.domain.query.AuthPolicyPageQuery;
import com.njydsz.userinfo.domain.vo.AuthPolicyVO;

/**
 * 认证策略仓储接口（P3-1 领域契约层）。
 *
 * <p>定义租户级认证策略数据访问能力，入参为领域 DTO / 基本类型字段，返回值为领域 VO。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>入参必须是 {@code dto/} 下的 DTO 或基本类型字段，禁止接受 infra 层 DO</li>
 *   <li>返回值必须是 {@code vo/} 下的 VO 或 {@code Optional<VO>}，禁止返回 infra 层持久化实体</li>
 *   <li>domain 层对 infra 层零感知，禁止 import {@code infra.entity} 包</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 2.24.0
 */
public interface AuthPolicyRepository {

  /**
   * 根据租户 ID 查询认证策略。
   *
   * @param tenantId 租户 ID；为空查询全局默认策略
   * @return 认证策略 VO；不存在返回 {@code Optional.empty()}
   */
  Optional<AuthPolicyVO> findByTenantId(String tenantId);

  /**
   * 分页查询认证策略列表。
   *
   * @param query 分页查询参数
   * @return 认证策略 VO 列表
   */
  List<AuthPolicyVO> findByPage(AuthPolicyPageQuery query);

  /**
   * 新增认证策略。
   *
   * @param dto 创建 DTO
   */
  void save(AuthPolicyCreateDTO dto);

  /**
   * 更新认证策略。
   *
   * @param tenantId 租户 ID
   * @param dto 更新 DTO
   */
  void update(String tenantId, AuthPolicyUpdateDTO dto);

  /**
   * 根据租户 ID 删除认证策略（逻辑删除）。
   *
   * @param tenantId 租户 ID
   */
  void deleteByTenantId(String tenantId);
}
