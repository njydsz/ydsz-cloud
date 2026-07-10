package com.njydsz.pmis.cronjob.entity.job;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.pmis.common.entity.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 慢任务诊断日志实体（pmis_job_slow_log 表，P6-3）。
 *
 * <p>当任务执行耗时超过 {@code pmis_job.slow_threshold_ms} 时，由 {@code SlowTaskDetector}
 * 自动记录到本表。与 {@link JobLogDO} 的区别：
 * <ul>
 *   <li>job_log 记录全部执行（RUNNING/SUCCESS/FAILED/TIMEOUT），用于审计</li>
 *   <li>slow_log 仅记录慢执行，用于性能趋势分析与优化决策</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pmis_job_slow_log")
public class JobSlowLogDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 ID */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 任务 ID（关联 pmis_job.id） */
    private String jobId;

    /** 任务 KEY（冗余，避免连表） */
    private String jobKey;

    /** 关联 pmis_job_log.id（原始终端执行日志） */
    private String logId;

    /** 本次执行耗时（毫秒） */
    private Long durationMs;

    /** 慢任务阈值（毫秒，来自 pmis_job.slow_threshold_ms） */
    private Long slowThresholdMs;

    /** 执行参数 JSON（冗余自 job_log，便于独立分析） */
    private String paramsJson;

    /** 异常信息（如慢且有异常，冗余自 job_log） */
    private String errorMessage;

    /** 链路追踪 ID */
    private String traceId;

    /** 租户 ID */
    private String tenantId;
}
