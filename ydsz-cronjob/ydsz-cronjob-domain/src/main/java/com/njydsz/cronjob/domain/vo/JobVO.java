package com.njydsz.cronjob.domain.vo;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

import lombok.Data;

/**
 * 定时任务视图对象
 *
 * <p>用于 Controller 层返回任务调度数据，对应实体 {@link com.njydsz.cronjob.domain.entity.job.Job}。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class JobVO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 主键 ID */
  private String id;

  /** 任务名称 */
  private String jobName;

  /** 任务分组 */
  private String jobGroup;

  /** 任务状态（NORMAL/PAUSED/AUTO_PAUSED/STOPPED/ERROR/DELETED，见 CronjobConstants） */
  private String status;

  /** 任务唯一标识 */
  private String jobKey;

  /** 处理器类全限定名 */
  private String handler;

  /** Cron 表达式 */
  private String cronExpression;

  /** 调度类型（CRON/FIXED_RATE/FIXED_DELAY） */
  private String scheduleType;

  /** 固定频率间隔（毫秒） */
  private Long fixedRateMs;

  /** 固定延迟间隔（毫秒） */
  private Long fixedDelayMs;

  /** 任务参数（JSON） */
  private String paramsJson;

  /** 任务备注 */
  private String jobRemark;

  /** 下次触发时间 */
  private LocalDateTime nextFireTime;

  /** 上次触发时间 */
  private LocalDateTime lastFireTime;

  /** 触发总次数 */
  private Long fireCount;

  /** 成功次数 */
  private Long successCount;

  /** 失败次数 */
  private Long failCount;

  /** 分布式锁 TTL（毫秒） */
  private Long lockTtlMs;

  /** 超时时间（毫秒） */
  private Long timeoutMs;

  /** 慢任务阈值（毫秒） */
  private Long slowThresholdMs;

  /** Misfire 策略 */
  private String misfirePolicy;

  /** 分片总数 */
  private Integer shardTotal;

  /** 任务类型 */
  private String jobType;

  /** 最大重试次数 */
  private Integer maxRetries;

  /** 重试间隔（毫秒） */
  private Long retryIntervalMs;

  /** 重试退避策略 */
  private String retryBackoff;

  /** 阻塞策略 */
  private String blockStrategy;

  /** 连续失败次数 */
  private Integer consecutiveFailCount;

  /** 最大连续失败次数 */
  private Integer maxConsecutiveFails;

  /** 自动恢复延迟（分钟） */
  private Integer autoResumeAfterMinutes;

  /** 优先级 */
  private Integer priority;

  /** 版本号 */
  private Integer version;

  /** 时区 */
  private String timezone;

  /** 集群 */
  private String cluster;

  /** 灰度比例 */
  private Integer canaryRatio;

  /** 灰度处理器 */
  private String canaryHandler;

  /** 创建人 */
  private String createdBy;

  /** 创建时间 */
  private LocalDateTime createdAt;

  /** 更新人 */
  private String updatedBy;

  /** 更新时间 */
  private LocalDateTime updatedAt;
}
