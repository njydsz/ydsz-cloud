package com.njydsz.pmis.cronjob.domain.entity.log;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import lombok.Data;

/**
 * 任务执行每日统计实体（P2-3 执行历史趋势可视化）。
 *
 * <p>对应 {@code pmis_job_daily_stats} 表，每天凌晨由 {@code DailyStatsAggregator}
 * 聚合 {@code pmis_job_log} 的执行数据，供前端趋势图展示（成功率/耗时折线图）。
 *
 * <p>注意：本表仅记录 {@code created_at}，不包含标准审计字段（updated_by/updated_at），
 * 因此不继承 {@code BaseDO}。每天 UPSERT 写入，不会产生更新操作。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@TableName("pmis_job_daily_stats")
public class JobDailyStatsDO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 ID */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 任务 ID */
    private String jobId;

    /** 任务 KEY（冗余） */
    private String jobKey;

    /** 统计日期 */
    private LocalDate statsDate;

    /** 当日触发次数 */
    private Long fireCount;

    /** 当日成功次数 */
    private Long successCount;

    /** 当日失败次数 */
    private Long failCount;

    /** 当日超时次数 */
    private Long timeoutCount;

    /** 平均耗时（毫秒） */
    private Long avgDurationMs;

    /** 最大耗时（毫秒） */
    private Long maxDurationMs;

    /** 最小耗时（毫秒） */
    private Long minDurationMs;

    /** P95 耗时（毫秒） */
    private Long p95DurationMs;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 逻辑删除标记: 0 未删除 / 1 已删除 */
    @TableLogic
    private Integer deleted;
}
