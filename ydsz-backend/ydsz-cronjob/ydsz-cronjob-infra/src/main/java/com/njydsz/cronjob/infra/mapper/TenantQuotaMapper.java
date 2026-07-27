package com.njydsz.cronjob.infra.mapper.job;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.cronjob.domain.entity.job.TenantQuota;

/**
 * 租户级配额 Mapper（P7-2）。
 *
 * <p>对应 ydsz_tenant_quota 表，存储每个租户的任务数/并发数/日执行量上限。
 *
 * <p><b>注意</b>：本表有 tenant_id 列，{@code TenantLineInnerInterceptor} 会自动追加
 * {@code WHERE tenant_id = ?}。由于本表按 tenant_id 唯一索引查询，自动过滤是正确行为
 * （租户只能看到自己的配额记录）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Mapper
public interface TenantQuotaMapper extends BaseMapper<TenantQuota> {

    /**
     * 按租户 ID 查询配额记录（用于配额检查）。
     *
     * <p>由于 {@code TenantLineInnerInterceptor} 会自动追加 {@code WHERE tenant_id = ?}，
     * 这里的 tenant_id 条件是冗余的（双重保证），不影响正确性。
     *
     * @param tenantId 租户 ID
     * @return 配额记录；不存在时返回 null
     */
    @Select("SELECT id, tenant_id, max_jobs, max_concurrent, max_daily_executions, enabled, "
            + "       created_by, created_at, updated_by, updated_at, deleted "
            + "FROM ydsz_tenant_quota "
            + "WHERE tenant_id = #{tenantId} AND deleted = 0")
    TenantQuota selectByTenantId(@Param("tenantId") String tenantId);
}
