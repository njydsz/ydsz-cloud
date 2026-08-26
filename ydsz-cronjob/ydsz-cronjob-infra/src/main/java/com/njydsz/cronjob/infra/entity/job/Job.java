package com.njydsz.cronjob.infra.entity.job;

import java.io.Serial;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.validation.constraints.NotBlank;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import com.njydsz.common.jdbc.entity.MpBaseEntity;

/**
 * 定时任务定义
 *
 * <p>对应 ydsz_job_main 表，描述一个调度任务的处理器、Cron 表达式、参数及执行统计。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_job_main")
public class Job extends MpBaseEntity<String> {

  @Serial private static final long serialVersionUID = 1L;

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

  private String scheduleType;
  private Long fixedRateMs;
  private Long fixedDelayMs;
  private String paramsJson;
  private String jobRemark;
  private LocalDateTime nextFireTime;
  private LocalDateTime lastFireTime;
  private Long fireCount;
  private Long successCount;
  private Long failCount;
  private Long lockTtlMs;
  private Long timeoutMs;
  private Long slaMs;
  private Long slowThresholdMs;
  private String misfirePolicy;
  private Integer shardTotal;
  private String jobType;
  private Integer maxRetries;
  private Long retryIntervalMs;
  private String retryBackoff;
  private String blockStrategy;
  private Integer consecutiveFailCount;
  private Integer maxConsecutiveFails;
  private Integer autoResumeAfterMinutes;
  private Integer priority;
  private Integer version;
  private String timezone;
  private String cluster;
  private Integer canaryRatio;
  private String canaryHandler;
}
