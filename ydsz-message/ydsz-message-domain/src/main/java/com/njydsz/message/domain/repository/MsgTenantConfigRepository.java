package com.njydsz.message.domain.repository;

import java.util.Optional;

import com.njydsz.message.domain.dto.MsgTenantConfigDTO;
import com.njydsz.message.domain.vo.MsgTenantConfigVO;

/**
 * 多租户消息配置仓储接口（domain 层契约）。
 *
 * <p>定义多租户消息配置的数据访问能力，Infra 层负责实现。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>以领域语义方法暴露数据访问能力，禁止 Mapper 透传
 *   <li>CUD 入参使用领域 DTO（{@link MsgTenantConfigDTO}）</li>
 *   <li>返回值使用领域 VO（{@link MsgTenantConfigVO}）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public interface MsgTenantConfigRepository {

  /**
   * 保存多租户消息配置（插入）。
   *
   * @param dto 多租户消息配置 DTO
   * @return 保存成功返回 {@code true}
   */
  boolean save(MsgTenantConfigDTO dto);

  /**
   * 更新多租户消息配置。
   *
   * @param dto 多租户消息配置 DTO（必须包含主键 ID）
   * @return 更新成功返回 {@code true}
   */
  boolean update(MsgTenantConfigDTO dto);

  /**
   * 根据主键 ID 查询多租户消息配置。
   *
   * @param id 配置 ID
   * @return 多租户消息配置 VO；不存在返回 {@code Optional.empty()}
   */
  Optional<MsgTenantConfigVO> findById(String id);

  /**
   * 根据租户 ID 查询多租户消息配置。
   *
   * @param tenantId 租户 ID
   * @return 多租户消息配置 VO；不存在返回 {@code Optional.empty()}
   */
  Optional<MsgTenantConfigVO> findByTenantId(String tenantId);
}
