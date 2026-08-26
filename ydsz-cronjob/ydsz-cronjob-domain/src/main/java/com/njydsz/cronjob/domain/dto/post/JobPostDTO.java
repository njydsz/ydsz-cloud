package com.njydsz.cronjob.domain.dto.post;

import java.io.Serial;
import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Job 新增请求 DTO。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class JobPostDTO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  @Schema(description = "主键 ID（创建时为空，由服务端 Snowflake 生成）")
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

  @Schema(
      description = "调度类型: CRON(Cron表达式, 默认) / FIXED_RATE(固定频率) / FIXED_DELAY(固定延迟) / API(仅手动触发)")
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

  @Min(value = 0, message = "最大重试次数不能为负")
  @Schema(description = "失败最大重试次数（null=不重试）")
  private Integer maxRetries;

  @Min(value = 1, message = "重试间隔必须为正数")
  @Schema(description = "重试间隔（毫秒）")
  private Long retryIntervalMs;

  @Schema(description = "重试退避策略（FIXED/EXPONENTIAL，null=默认 FIXED）")
  private String retryBackoff;

  @Min(value = 1, message = "SLA 阈值必须为正数")
  @Schema(description = "SLA 达标阈值（毫秒）：执行耗时超过此值触发 SLA_WARNING 告警，null=不设 SLA")
  private Long slaMs;

  @Schema(
      description = "阻塞策略: SERIAL 串行跳过(默认) / DISCARD 丢弃本次 / DISCARD_OVERLAPPING 丢弃重叠 / COVER 覆盖执行 / CONCURRENT 并行执行")
  private String blockStrategy;

  @Min(value = 1, message = "最大连续失败次数必须 >= 1")
  @Schema(description = "最大连续失败次数（达到后自动熔断暂停任务，null=不熔断）")
  private Integer maxConsecutiveFails;

  @Min(value = 1, message = "自动恢复延迟必须为正数")
  @Schema(description = "自动恢复延迟（分钟）：熔断后超过此时间自动恢复 NORMAL，null=不自动恢复")
  private Integer autoResumeAfterMinutes;

  @Min(value = 0, message = "优先级不能为负")
  @Schema(description = "调度优先级（数值越大越先派发，默认 0）")
  private Integer priority;

  @Min(value = 0, message = "灰度比例必须在 0-100")
  @Schema(description = "灰度比例（0-100，jobKey 稳定哈希分桶，null=全量走主 handler）")
  private Integer canaryRatio;

  @Schema(description = "灰度处理器 Bean 名称（canaryRatio>0 时生效）")
  private String canaryHandler;

  @Schema(description = "租户 ID（null 时由服务端从 TenantContextHolder 注入）")
  private String tenantId;
}
