package com.remisoft.cronjob.domain.entity.job;

import java.io.Serial;

import com.baomidou.mybatisplus.annotation.TableName;
import com.remisoft.common.jdbc.entity.MpBaseEntity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 租户级配额实体（P7-2）。
 *
 * <p>对应 {@code remi_tenant_quota} 表，存储每个租户的任务数 / 并发数 / 日执行量上限。
 * 表物理位置在 remi-cronjob 模块（{@code TenantQuotaMapper} 在 remi-cronjob-infra），
 * 因此本实体归属 remi-cronjob-domain，不归 remi-system。
 *
 * @author remi-team
 * @since 1.0.0
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("remi_tenant_quota")
public class TenantQuota extends MpBaseEntity<String> {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 任务数上限（NULL=unlimited；超过此值拒绝创建新任务） */
    private Integer maxJobs;

    /** 并发执行上限（NULL=unlimited；超过此值拒绝派发，P7-3 实现） */
    private Integer maxConcurrent;

    /** 日执行量上限（NULL=unlimited；超过此值拒绝派发，P7-3 实现） */
    private Integer maxDailyExecutions;

    /** 是否启用配额检查: 0 禁用 / 1 启用 */
    private Integer enabled;
}
