package com.njydsz.userinfo.domain.repository;

import java.util.List;
import java.util.Optional;

import com.njydsz.userinfo.domain.dto.AuthPolicyDTO;
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
 * @since 26.09.01
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
   * 保存认证策略（创建或更新）。
   *
   * <p>统一 DTO：创建时 {@code tenantId} 可不传（为空表示全局默认策略），
   * 更新时 {@code tenantId} 必填。更新时仅修改非 null 字段。
   *
   * @param dto 认证策略 DTO
   */
  void save(AuthPolicyDTO dto);

  /**
   * 根据租户 ID 删除认证策略（逻辑删除）。
   *
   * @param tenantId 租户 ID
   */
  void deleteByTenantId(String tenantId);
}
