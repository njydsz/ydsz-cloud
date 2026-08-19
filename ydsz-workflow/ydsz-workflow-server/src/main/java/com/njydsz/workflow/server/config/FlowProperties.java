package com.njydsz.workflow.server.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * 工作流模块统一配置属性。
 *
 * <p>配置前缀：{@code ydsz.flow}
 *
 * <p>典型配置示例：
 *
 * <pre>
 * ydsz:
 *   flow:
 *     enabled: true
 *     health-enabled: true
 *     publish-block-on-high-risk: true
 *     designer-lock-timeout-minutes: 30
 * </pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@Validated
@ConfigurationProperties(prefix = "ydsz.flow")
public class FlowProperties {

  /** 是否启用工作流模块 */
  private boolean enabled = true;

  /** 是否启用健康检查 */
  private boolean healthEnabled = true;

  /** 发布流程时是否阻断 HIGH 风险（在途实例卡在已删除节点）。true=阻断（推荐）；false=仅警告 */
  private boolean publishBlockOnHighRisk = true;

  /** 设计器协同编辑锁定超时阈值（分钟） */
  @Min(1)
  private long designerLockTimeoutMinutes = 30L;

  /** P3-3.4: 子流程嵌套配置 */
  private SubProcess subProcess = new SubProcess();

  /** P3-3.4: 附件预览配置 */
  private Attachment attachment = new Attachment();

  /** 自动催办配置 */
  private AutoUrge autoUrge = new AutoUrge();

  /** P1-2: 流程定义缓存配置（节点/连线/SourceRef 索引三级缓存统一 TTL 与容量） */
  private DefinitionCache definitionCache = new DefinitionCache();

  /** P3-1: 流程历史数据归档配置（原 FlowHistoryProperties 合并） */
  private History history = new History();

  /**
   * P3-3.4: 子流程嵌套配置。
   *
   * <p>由 {@link com.njydsz.workflow.server.service.impl.instance.FlowSubProcessServiceImpl} 消费。
   */
  @Data
  public static class SubProcess {
    /** 最大子流程嵌套深度（默认 3 层，建议不超过 10 层） */
    private int maxNestingDepth = 3;
  }

  /**
   * P3-3.4: 附件预览配置。
   *
   * <p>由 {@link com.njydsz.workflow.server.service.impl.integration.FlowAttachmentServiceImpl} 消费。
   */
  @Data
  public static class Attachment {
    /** 外部预览服务地址（kkFileView/Office Online），如 http://preview.example.com/onlinePreview?url={url} */
    private String previewServerUrl = "";
  }

  /**
   * 自动催办配置。
   *
   * <p>由 {@link com.njydsz.workflow.server.scheduler.FlowAutoUrgeScheduler} 消费。
   */
  @Data
  public static class AutoUrge {
    /** 超时阈值（小时），任务创建后超过此时间未处理则触发自动催办 */
    private long thresholdHours = 24;

    /** 单次扫描批量大小 */
    private int batchSize = 100;
  }

  /**
   * P3-1: 流程历史数据归档配置。
   *
   * <p>由 {@link com.njydsz.workflow.server.service.impl.FlowHistoryArchiveServiceImpl} 消费。
   */
  @Data
  public static class History {
    /** 是否启用自动归档（JobHandler 调度时检查，false 则跳过执行） */
    private boolean archiveEnabled = true;

    /** 归档阈值天数：已结束实例结束时间超过该天数后归档（默认 30 天） */
    @Min(1)
    private int retentionDays = 30;

    /** 单次归档批量大小：每次扫描最多处理的实例数（默认 100） */
    @Min(1)
    @Max(10000)
    private int batchSize = 100;

    /** 单次归档最大耗时（毫秒）：达到上限后剩余实例留待下次执行（默认 30 秒） */
    @Min(1000)
    private long maxProcessMs = 30_000L;

    /** 归档任务 cron 表达式（用于 ydsz_job 表配置参考，默认每日 03:00） */
    private String cronExpression = "0 0 3 * * ?";

    /** 是否启用归档数据清理（purge）：清理已归档超过 purgeDays 的冷数据，默认关闭 */
    private boolean purgeEnabled = false;

    /** 归档数据清理阈值天数：archived_at 超过该天数的归档记录将被物理删除（默认 5 年 = 1825 天） */
    @Min(30)
    private int purgeDays = 1825;
  }

  /**
   * P1-2: 流程定义缓存配置。
   *
   * <p>节点缓存（{@code flow:def-nodes}）、连线缓存（{@code flow:def-skips}）、SourceRef 索引缓存
   * （{@code flow:def-sourceref-index}）三级缓存统一使用此配置。TTL 与容量通过 YAML 外部化，禁止硬编码。
   *
   * <p>由 {@link com.njydsz.workflow.server.engine.FlowDefinitionCacheService} 消费。
   */
  @Data
  public static class DefinitionCache {
    /** 缓存过期时间（分钟），所有流程定义缓存统一 TTL（默认 60 分钟） */
    @Min(1)
    private long definitionCacheTtlMinutes = 60L;

    /** 缓存最大容量（条目数），所有流程定义缓存统一上限（默认 1000 条） */
    @Min(1)
    private long definitionCacheMaxSize = 1000L;
  }
}
