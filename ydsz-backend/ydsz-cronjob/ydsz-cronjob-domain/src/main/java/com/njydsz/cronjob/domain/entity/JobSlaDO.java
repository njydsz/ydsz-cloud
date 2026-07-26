package com.njydsz.cronjob.domain.entity.alert;

import java.io.Serial;
import java.math.BigDecimal;

import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.common.jdbc.entity.MpBaseEntity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 任务 SLA 管理实体（P2-7 SLA 管理, P2-2-merge 重构为视图 DTO）。
 *
 * <p>P2-2-merge: 原对应 {@code ydsz_job_sla} 表，现已迁移到 {@code ydsz_job_alert_rule}
 * （source_type='SLA'）。本类保留为视图 DTO，由 {@code JobSlaServiceImpl} 从多条
 * alert_rule 记录聚合而成，对外 API 保持兼容。
 * 由 {@code AlertScanner} 统一扫描 source_type='SLA' 的规则并触发告警。
 *
 * <h3>约束字段</h3>
 * <ul>
 *   <li>{@link #maxDurationMs}：最大执行时长（毫秒），超过则违约</li>
 *   <li>{@link #maxFailRate}：最大失败率（百分比 0-100），超过则违约</li>
 *   <li>{@link #minSuccessRate}：最小成功率（百分比 0-100），低于则违约</li>
 * </ul>
 *
 * <p>三个约束字段至少配置一项，未配置（null）的项不参与检查。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_job_sla")
public class JobSlaDO extends MpBaseEntity<String> {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 任务 ID */
    private String jobId;

    /** 任务 KEY（冗余） */
    private String jobKey;

    /** 最大执行时长（毫秒），超过则违约；null 表示不检查 */
    private Long maxDurationMs;

    /** 最大失败率（%），超过则违约；null 表示不检查 */
    private BigDecimal maxFailRate;

    /** 最小成功率（%），低于则违约；null 表示不检查 */
    private BigDecimal minSuccessRate;

    /** 告警级别: INFO / WARNING / CRITICAL */
    private String alertLevel;

    /** 是否启用: 0 禁用 / 1 启用 */
    private Integer enabled;
}
