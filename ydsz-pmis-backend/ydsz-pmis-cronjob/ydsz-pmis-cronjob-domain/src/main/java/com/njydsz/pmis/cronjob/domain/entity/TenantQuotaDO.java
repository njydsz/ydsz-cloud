package com.njydsz.pmis.cronjob.domain.entity.job;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.pmis.common.domain.entity.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 租户级配额实体（pmis_tenant_quota 表，P7-2）。
 *
 * <p>控制单个租户可创建的任务数、并发执行数、日执行量上限，防止 noisy neighbor 问题。
 * 未配置记录的租户视为 unlimited（由应用层 {@code CronjobProperties.Quota.defaultMax*} 兜底）。
 *
 * <h3>字段说明</h3>
 * <ul>
 *   <li>{@link #maxJobs} - 任务数上限，{@code null} 表示 unlimited</li>
 *   <li>{@link #maxConcurrent} - 并发执行上限，{@code null} 表示 unlimited（P7-3 实现）</li>
 *   <li>{@link #maxDailyExecutions} - 日执行量上限，{@code null} 表示 unlimited（P7-3 实现）</li>
 *   <li>{@link #enabled} - 是否启用配额检查（0 禁用 / 1 启用）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pmis_tenant_quota")
public class TenantQuotaDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 ID */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 租户 ID（唯一，一个租户一条配额记录） */
    private String tenantId;

    /** 任务数上限（NULL=unlimited；超过此值拒绝创建新任务） */
    private Integer maxJobs;

    /** 并发执行上限（NULL=unlimited；超过此值拒绝派发，P7-3 实现） */
    private Integer maxConcurrent;

    /** 日执行量上限（NULL=unlimited；超过此值拒绝派发，P7-3 实现） */
    private Integer maxDailyExecutions;

    /** 是否启用配额检查: 0 禁用 / 1 启用 */
    private Integer enabled;
}
