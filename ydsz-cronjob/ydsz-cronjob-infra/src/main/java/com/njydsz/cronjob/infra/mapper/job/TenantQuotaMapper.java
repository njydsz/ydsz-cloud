package com.njydsz.cronjob.infra.mapper.job;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.njydsz.cronjob.infra.entity.job.TenantQuota;

/**
 * 租户任务配额 Mapper
 *
 * <p>对应数据表 <code>ydsz_tenant_quota</code>。
 *
 * <p>配额限制租户可创建的任务数/并发数/触发频率，是多租户隔离的资源管控。
 *
 * <p><b>主要索引：</b>
 *
 * <ul>
 *   <li>uk_tenant_id — 租户 ID 唯一索引
 * </ul>
 *
 * <p><b>多租户：</b>由 MyBatis 拦截器自动注入 {@code tenant_id} 过滤条件，本接口不感知。
 *
 * <p><b>逻辑删除：</b>{@code deleted} 字段标识，所有查询自动过滤已删除记录。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see com.njydsz.cronjob.domain.entity.job.TenantQuota 配额实体
 * @see com.njydsz.cronjob.server.service.TenantQuotaService 配额 Service
 * @see com.baomidou.mybatisplus.core.mapper.BaseMapper MyBatis-Plus 通用 Mapper
 */
@Mapper
public interface TenantQuotaMapper extends BaseMapper<TenantQuota> {

  /**
   * 按租户 ID 查询配额记录（用于配额检查）。
   *
   * <p>由于 {@code TenantLineInnerInterceptor} 会自动追加 {@code WHERE tenant_id = ?}， 这里的 tenant_id
   * 条件是冗余的（双重保证），不影响正确性。
   *
   * @param tenantId 租户 ID
   * @return 配额记录；不存在时返回 null
   */
  @Select(
      "SELECT id, tenant_id, max_jobs, max_concurrent, max_daily_executions, enabled, "
          + "       created_by, created_at, updated_by, updated_at, deleted "
          + "FROM ydsz_tenant_quota "
          + "WHERE tenant_id = #{tenantId} AND deleted = 0")
  TenantQuota selectByTenantId(@Param("tenantId") String tenantId);
}
