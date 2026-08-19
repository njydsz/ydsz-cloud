package com.njydsz.cronjob.domain.repository;

import java.util.Optional;

import com.njydsz.cronjob.domain.entity.job.TenantQuota;
import com.njydsz.cronjob.domain.vo.TenantQuotaVO;

/**
 * 租户配额 Repository（domain 层契约）。
 *
 * <p>定义租户级配额的数据访问能力，Infra 层负责实现。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>以领域语义方法暴露数据访问能力，禁止 Mapper 透传
 *   <li>返回领域 VO（{@link TenantQuotaVO}），非 DTO / infra 实体
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface TenantQuotaRepository {

  /**
   * 根据租户 ID 查询配额记录。
   *
   * @param tenantId 租户 ID
   * @return 配额 VO；不存在返回 {@code Optional.empty()}
   */
  Optional<TenantQuotaVO> findByTenantId(String tenantId);

  /** 按租户 ID 查询配额实体（TenantQuotaServiceImpl 计量/更新使用）。 */
  TenantQuota selectByTenantId(String tenantId);
}
