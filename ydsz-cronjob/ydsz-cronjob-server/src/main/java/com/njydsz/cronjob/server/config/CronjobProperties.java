package com.njydsz.cronjob.server.config;

import java.time.Duration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

/**
 * 定时任务调度服务配置属性（P2-4: 配置类拆分重构）。
 *
 * <p>支持在 application.yml / Nacos 中通过 {@code ydsz.cronjob.*} 前缀进行动态覆盖。
 *
 * <p>原内部类已拆分为独立配置文件，本类作为组合根（Composite Root）统一暴露各子配置。
 *
 * <h3>关键配置项</h3>
 *
 * <ul>
 *   <li>{@link #getJobLockTtl()} 分布式锁默认 TTL（任务级未配置时使用）
 *   <li>{@link #getJobLockTtlMin()} 任务级 TTL 下限（防止误配置为过短导致并发执行）
 *   <li>{@link #getJobLockTtlMax()} 任务级 TTL 上限（防止误配置为过长导致锁不释放）
 *   <li>{@link #getLeader()} Leader 选举配置
 *   <li>{@link #getScanner()} 任务扫描器配置
 *   <li>{@link #getExecutor()} 执行器配置
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@Configuration
@Validated
@ConfigurationProperties(prefix = "ydsz.cronjob")
public class CronjobProperties {

  /** 分布式锁默认 TTL（兜底值，任务级未配置时使用） */
  private Duration jobLockTtl = Duration.ofMinutes(5);

  /** 任务级 TTL 下限：防止误配置为过短（&lt; 30s）导致长任务执行中被并发抢占 */
  private Duration jobLockTtlMin = Duration.ofSeconds(30);

  /** 任务级 TTL 上限：防止误配置为过长（&gt; 24h）导致锁不释放 */
  private Duration jobLockTtlMax = Duration.ofHours(24);

  /** 调度器线程池大小 */
  private int schedulerPoolSize = 8;

  /** 调度器优雅关闭等待时间（秒） */
  private int schedulerAwaitTerminationSeconds = 30;

  /** Leader 选举配置 */
  private LeaderConfig leader = new LeaderConfig();

  /** 任务扫描器配置 */
  private ScannerConfig scanner = new ScannerConfig();

  /** 执行器配置 */
  private ExecutorConfig executor = new ExecutorConfig();

  /** 租户级配额配置（P7-2 新增） */
  private QuotaConfig quota = new QuotaConfig();

  /** HTTP 任务配置（P1-5 新增） */
  private HttpConfig http = new HttpConfig();

  /** 远程派发配置（P1-4 新增） */
  private RemoteConfig remote = new RemoteConfig();

  /** 告警扫描配置（P3-2 新增：周期性告警扫描使用） */
  private AlertScanConfig alert = new AlertScanConfig();

  /** P1-1: 节点发现策略配置（Nacos 服务发现 / 心跳表） */
  private NodeDiscoveryConfig nodeDiscovery = new NodeDiscoveryConfig();

  /** P2-2: 日志归档清理配置 */
  private LogRetentionConfig logRetention = new LogRetentionConfig();

  /** P0-1: MapReduce 分布式并行执行配置 */
  private MapReduceConfig mapReduce = new MapReduceConfig();

  /** P0-1: 集群级配置 */
  private ClusterConfig cluster = new ClusterConfig();

  /** P3-11: 脚本执行沙箱配置 */
  private SandboxConfig sandbox = new SandboxConfig();

  /** P3-3.3: 任务制品（Artifact）存储配置 */
  private ArtifactConfig artifact = new ArtifactConfig();

  /** P0-1: 调度器-执行器分离配置 */
  private SchedulerExecutorSeparationConfig schedulerExecutorSeparation =
      new SchedulerExecutorSeparationConfig();

  /** P1-1: 自适应批量调度配置 */
  private AdaptiveBatchConfig adaptiveBatch = new AdaptiveBatchConfig();

  /** P1-4: 异常修复统一配置（合并原 Failover + SelfHealing） */
  private AnomalyRecoveryConfig anomalyRecovery = new AnomalyRecoveryConfig();

  /** SpEL 表达式缓存配置（已废弃，v1.2.0 移除） */
  @Deprecated
  private SpelConfig spel = new SpelConfig();

  /**
   * 校验并规整化 TTL 值。
   *
   * <p>若传入 TTL 为 null，返回默认值；若超出 [min, max] 区间，自动收敛到边界值。
   *
   * @param ttl 任务级配置的 TTL（可为 null）
   * @return 规整化后的 TTL
   */
  public Duration normalizeTtl(Duration ttl) {
    if (ttl == null || ttl.isZero() || ttl.isNegative()) {
      return jobLockTtl;
    }
    if (ttl.compareTo(jobLockTtlMin) < 0) {
      return jobLockTtlMin;
    }
    if (ttl.compareTo(jobLockTtlMax) > 0) {
      return jobLockTtlMax;
    }
    return ttl;
  }
}
