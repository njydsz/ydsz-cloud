package com.njydsz.message.domain.event;

import java.io.Serial;

/**
 * 批次进度变更领域事件。
 *
 * <p>在批次处理进度更新时发布，携带批次总量、成功数、失败数、跳过数与进度百分比。 订阅者可据此更新批次监控面板、触发进度回调等。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class BatchProgressChangedEvent extends MessageDomainEvent {

  @Serial private static final long serialVersionUID = 1L;

  /** 批次 ID */
  private final String batchId;

  /** 总量 */
  private final int total;

  /** 成功数 */
  private final int success;

  /** 失败数 */
  private final int failed;

  /** 跳过数 */
  private final int skipped;

  /** 进度百分比（0.0 ~ 100.0） */
  private final double progressPercent;

  /**
   * 构造批次进度变更事件。
   *
   * @param tenantId 租户 ID
   * @param batchId 批次 ID
   * @param total 总量
   * @param success 成功数
   * @param failed 失败数
   * @param skipped 跳过数
   * @param progressPercent 进度百分比
   */
  public BatchProgressChangedEvent(
      String tenantId,
      String batchId,
      int total,
      int success,
      int failed,
      int skipped,
      double progressPercent) {
    super(tenantId, null, batchId);
    this.batchId = batchId;
    this.total = total;
    this.success = success;
    this.failed = failed;
    this.skipped = skipped;
    this.progressPercent = progressPercent;
  }

  public String getBatchId() {
    return batchId;
  }

  public int getTotal() {
    return total;
  }

  public int getSuccess() {
    return success;
  }

  public int getFailed() {
    return failed;
  }

  public int getSkipped() {
    return skipped;
  }

  public double getProgressPercent() {
    return progressPercent;
  }
}
