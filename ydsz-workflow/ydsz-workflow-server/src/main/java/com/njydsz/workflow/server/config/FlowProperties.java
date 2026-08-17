package com.njydsz.workflow.server.config;

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
}
