package com.njydsz.pmis.cronjob.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.njydsz.pmis.common.entity.BaseDO;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;
import java.time.LocalDateTime;

/**
 * 定时任务定义
 *
 * <p>对应 pmis_job 表，描述一个调度任务的处理器、Cron 表达式、参数及执行统计。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pmis_job")
public class JobDO extends BaseDO {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 ID */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 任务名称 */
    @NotBlank(message = "{validation.cronjob.msg_f96f7bb7}")
    private String jobName;

    /** 任务分组 */
    private String jobGroup;

    /** 任务 KEY（唯一） */
    @NotBlank(message = "{validation.cronjob.msg_fcfe1413}")
    private String jobKey;

    /** 任务处理器 Bean 名称 */
    @NotBlank(message = "{validation.cronjob.msg_4b699261}")
    private String handler;

    /** Cron 表达式 */
    @NotBlank(message = "{validation.cronjob.msg_14201280}")
    private String cronExpression;

    /** 参数 JSON */
    private String paramsJson;

    /** 状态: NORMAL/PAUSED/ERROR */
    private String status;

    /** 备注 */
    private String remark;

    /** 下次触发时间 */
    private LocalDateTime nextFireTime;

    /** 上次触发时间 */
    private LocalDateTime lastFireTime;

    /** 触发次数 */
    private Long fireCount;

    /** 成功次数 */
    private Long successCount;

    /** 失败次数 */
    private Long failCount;

    /** 任务级锁 TTL（毫秒，null 使用全局默认值） */
    private Long lockTtlMs;

    /** 任务超时时间（毫秒，null 表示不限超时） */
    private Long timeoutMs;

    /**
     * 慢任务阈值（毫秒，P6-3）。
     *
     * <p>null 表示不检测慢任务；执行耗时超过此值时记入 pmis_job_slow_log。
     */
    private Long slowThresholdMs;

    /**
     * Misfire 策略（P2-1）：FIRE_NOW / SKIP / COALESCE。
     *
     * <p>当 next_fire_time 早于 NOW() - misfireGraceMinutes 时按本策略处理。
     * null 视为 {@link com.njydsz.pmis.cronjob.core.dispatch.MisfirePolicy#FIRE_NOW}。
     */
    private String misfirePolicy;

    /**
     * 分片总数（P3-3）：&gt;= 1，1 表示非分片任务（默认）。
     *
     * <p>当 shardTotal &gt; 1 时，Leader 通过 {@code ShardingStrategy} 将分片分配到在线节点，
     * 每个节点仅执行分配给自己的分片，实现数据并行处理。对标 XXL-Job 的分片广播。
     */
    private Integer shardTotal;

    /** 租户 ID */
    private String tenantId;
}
