package com.njydsz.cronjob.domain.dto.put;


import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.io.Serial;
import java.io.Serializable;
import lombok.Data;

/**
 * Job 修改请求 DTO。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class JobPutDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @NotBlank(message = "ID不能为空")
    @Schema(description = "任务 ID（更新时必填）")
    private String id;

    @NotBlank(message = "{validation.cronjob.msg_f96f7bb7}")
    @Schema(description = "任务名称", requiredMode = Schema.RequiredMode.REQUIRED)
    private String jobName;

    @Schema(description = "任务分组")
    private String jobGroup;

    @NotBlank(message = "{validation.cronjob.msg_fcfe1413}")
    @Schema(description = "任务 KEY（唯一）", requiredMode = Schema.RequiredMode.REQUIRED)
    private String jobKey;

    @NotBlank(message = "{validation.cronjob.msg_4b699261}")
    @Schema(description = "任务处理器 Bean 名称", requiredMode = Schema.RequiredMode.REQUIRED)
    private String handler;

    @Schema(description = "Cron 表达式（scheduleType=CRON 时必填）")
    private String cronExpression;

    @Schema(description = "调度类型: CRON(Cron表达式, 默认) / FIXED_RATE(固定频率) / FIXED_DELAY(固定延迟) / API(仅手动触发)")
    private String scheduleType;

    @Min(value = 1, message = "固定频率间隔必须为正数")
    @Schema(description = "固定频率间隔（毫秒, scheduleType=FIXED_RATE 时生效, 如 30000=每 30 秒执行一次）")
    private Long fixedRateMs;

    @Min(value = 1, message = "固定延迟间隔必须为正数")
    @Schema(description = "固定延迟间隔（毫秒, scheduleType=FIXED_DELAY 时生效, 上次完成后等待此毫秒数再执行）")
    private Long fixedDelayMs;

    @Schema(description = "参数 JSON")
    private String paramsJson;

    @Schema(description = "状态: NORMAL/PAUSED/ERROR")
    private String status;

    @Schema(description = "备注")
    private String remark;

    @Min(value = 1, message = "任务级锁 TTL 必须为正数")
    @Schema(description = "任务级分布式锁 TTL（毫秒，null 使用全局默认值）")
    private Long lockTtlMs;

    @Min(value = 1, message = "任务超时时间必须为正数")
    @Schema(description = "任务超时时间（毫秒，null 表示不限超时）")
    private Long timeoutMs;

    @Min(value = 1, message = "慢任务阈值必须为正数")
    @Schema(description = "慢任务阈值（毫秒）：null 不检测慢任务；执行耗时超过此值时由 SlowTaskDetector 标记 JobLog.is_slow=1")
    private Long slowThresholdMs;

    @Schema(description = "Misfire 策略: FIRE_NOW 立即执行(默认) / SKIP 跳过 / COALESCE 合并执行")
    private String misfirePolicy;

    @Min(value = 1, message = "分片总数必须 >= 1")
    @Schema(description = "分片总数（1=非分片任务，>1 时按 ShardingStrategy 分配到在线节点并行执行）")
    private Integer shardTotal;

    @Schema(description = "任务时区（如 Asia/Shanghai / America/New_York / UTC，null 使用默认）")
    private String timezone;

    @Schema(description = "目标集群名称（P3-12 跨集群调度，null=本地集群）")
    private String cluster;

}
