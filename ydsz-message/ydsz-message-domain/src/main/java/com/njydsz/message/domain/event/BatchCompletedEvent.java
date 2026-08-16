package com.njydsz.message.domain.event;

import java.io.Serial;

/**
 * 批次完成领域事件。
 *
 * <p>在批次处理完成后发布，携带最终统计信息。
 *
 * @author ydsz-team
 * @since 1.2.0
 */
public class BatchCompletedEvent extends MessageDomainEvent {

  @Serial private static final long serialVersionUID = 1L;

  /** 总数 */
  private final int total;

  /** 成功数 */
  private final int success;

  /** 失败数 */
  private final int failed;

  /** 跳过数 */
  private final int skipped;

  /** 执行模式（FULL / RESUME） */
  private final String mode;

  /**
   * 构造批次完成事件。
   *
   * @param tenantId 租户 ID
   * @param batchId 批次 ID
   * @param total 总数
   * @param success 成功数
   * @param failed 失败数
   * @param skipped 跳过数
   * @param mode 执行模式
   */
  public BatchCompletedEvent(
      String tenantId,
      String batchId,
      int total,
      int success,
      int failed,
      int skipped,
      String mode) {
    super(tenantId, null, batchId);
    this.total = total;
    this.success = success;
    this.failed = failed;
    this.skipped = skipped;
    this.mode = mode;
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

  public String getMode() {
    return mode;
  }
}
