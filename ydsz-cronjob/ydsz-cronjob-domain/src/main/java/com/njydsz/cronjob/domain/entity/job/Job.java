package com.njydsz.cronjob.domain.entity.job;

import java.io.Serial;
import java.time.LocalDateTime;

import com.baomidou.mybatisplus.annotation.TableName;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import com.njydsz.common.jdbc.entity.MpBaseEntity;

/**
 * 定时任务定义
 *
 * <p>对应 ydsz_job 表，描述一个调度任务的处理器、Cron 表达式、参数及执行统计。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_job")
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

  /**
   * 调度类型（P0-3）：CRON / FIXED_RATE / FIXED_DELAY / API。
   *
   * <p>null 视为 CRON（向后兼容）。
   */
  private String scheduleType;

  /**
   * 固定频率间隔（毫秒，P0-3）：scheduleType=FIXED_RATE 时生效。
   *
   * <p>如 30000 = 每 30 秒执行一次。
   */
  private Long fixedRateMs;

  /**
   * 固定延迟间隔（毫秒，P0-3）：scheduleType=FIXED_DELAY 时生效。
   *
   * <p>上次执行完成后等待此毫秒数再执行下一次。
   */
  private Long fixedDelayMs;

  /** 参数 JSON */
  private String paramsJson;

  /** 备注 */
  private String jobRemark;

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
   * SLA 时效阈值（毫秒，P2-F2）。
   *
   * <p>任务执行耗时的 SLA 承诺值，超过 80% 时发送预警，达到 100% 时发送告警。
   * null 表示不进行 SLA 监控。与 timeoutMs 独立：timeoutMs 是硬超时（强制终止），
   * slaMs 是软承诺（仅告警，不中断执行）。
   *
   * <p>示例：slaMs=60000 表示承诺 60 秒内完成，48 秒时预警，60 秒时告警。
   */
  private Long slaMs;

  /**
   * 慢任务阈值（毫秒，P6-3）。
   *
   * <p>null 表示不检测慢任务；执行耗时超过此值时由 SlowTaskDetector 标记 {@code ydsz_job_log.is_slow=1}，用于性能趋势分析。
   */
  private Long slowThresholdMs;

  /**
   * Misfire 策略（P2-1）：FIRE_NOW / SKIP / COALESCE。
   *
   * <p>当 next_fire_time 早于 NOW() - misfireGraceMinutes 时按本策略处理。 null 视为 {@link
   * com.njydsz.cronjob.server.core.dispatch.MisfirePolicy#FIRE_NOW}。
   */
  private String misfirePolicy;

  /**
   * 分片总数（P3-3）：&gt;= 1，1 表示非分片任务（默认）。
   *
   * <p>当 shardTotal &gt; 1 时，Leader 通过 {@code ShardingStrategy} 将分片分配到在线节点，
   * 每个节点仅执行分配给自己的分片，实现数据并行处理。对标 XXL-Job 的分片广播。
   */
  private Integer shardTotal;

  /**
   * 任务类型（P1-5）：BEAN / HTTP / SHELL / GLUE。
   *
   * <p>BEAN: Spring Bean 处理器（默认）；HTTP: HTTP 调用；SHELL: 脚本；GLUE: 在线代码。
   */
  private String jobType;

  /** 最大重试次数（P1-1）：0=不重试（默认），&gt;0 时失败后自动重试。 */
  private Integer maxRetries;

  /** 重试间隔（毫秒，P1-1）：null=立即重试，&gt;0 时按 retryBackoff 策略计算间隔。 */
  private Long retryIntervalMs;

  /** 重试退避策略（P1-1）：FIXED 固定间隔 / EXPONENTIAL 指数退避。 */
  private String retryBackoff;

  /**
   * 阻塞策略（P1-2）：SERIAL / COVER / DISCARD / CONCURRENT。
   *
   * <p>任务正在执行时下一次触发如何处理：
   *
   * <ul>
   *   <li>SERIAL: 排队等待（默认，通过 Redis 锁互斥实现）
   *   <li>COVER: 中断当前执行新任务
   *   <li>DISCARD: 丢弃新触发
   *   <li>CONCURRENT: 并行执行（不加锁）
   * </ul>
   */
  private String blockStrategy;

  /** 连续失败次数（P1-6）：成功时归零，失败时 +1。 */
  private Integer consecutiveFailCount;

  /** 最大连续失败次数（P1-6）：null=不熔断，&gt;0 时达到阈值后 status 改为 AUTO_PAUSED。 */
  private Integer maxConsecutiveFails;

  /** 自动恢复时间（分钟，P1-6）：null=不自动恢复，&gt;0 时 AUTO_PAUSED 后定时检查恢复。 */
  private Integer autoResumeAfterMinutes;

  /** 优先级（P4-7）：1-10，越小越高（默认 5）。 */
  private Integer priority;

  /** 版本号（P4-8）：每次修改 +1，用于乐观锁和版本追溯。 */
  private Integer version;

  /** 任务级时区（P2-8）：如 Asia/Shanghai / America/New_York / UTC。 null 使用系统默认时区（Asia/Shanghai）。 */
  private String timezone;

  /**
   * 目标集群名称（P3-12 跨集群调度）。
   *
   * <p>null 或空表示本地集群（默认）； 非 null 时任务通过 {@code CrossClusterDispatcher} 派发到指定集群执行。
   */
  private String cluster;

  /**
   * P1-6: 灰度发布比例（0-100，0=不灰度，100=全量灰度）。
   *
   * <p>当 canaryRatio > 0 时，每次任务执行有 canaryRatio% 的概率 使用 {@link #canaryHandler} 而非 {@link #handler}
   * 执行。 用于灰度发布新版本处理器，对标 SchedulerX 的灰度发布能力。
   */
  private Integer canaryRatio;

  /**
   * P1-6: 灰度处理器 Bean 名称（canaryRatio > 0 时生效）。
   *
   * <p>灰度流量使用此 handler 执行，非灰度流量仍使用 {@link #handler}。 灰度验证通过后，将 canaryHandler 复制到 handler 并将
   * canaryRatio 设为 0 即可完成全量发布。
   */
  private String canaryHandler;
}
