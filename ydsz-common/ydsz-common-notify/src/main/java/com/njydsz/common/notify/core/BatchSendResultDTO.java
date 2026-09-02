package com.njydsz.common.notify.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 批量发送结构化结果
 *
 * <p>提供每个接收者的发送明细，便于业务方定位失败接收者并执行定向重试。 与 {@link NotifySendResult} 不同，本对象包含完整的逐条发送结果。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public class BatchSendResultDTO {

  /** 接收者总数 */
  private final int totalCount;

  /** 成功数 */
  private final int successCount;

  /** 失败数 */
  private final int failureCount;

  /** 各接收者发送明细（不可变） */
  private final List<ReceiverSendResult> details;

  /**
   * 构造批量发送结果
   *
   * @param totalCount 接收者总数
   * @param successCount 成功数
   * @param failureCount 失败数
   * @param details 各接收者发送明细
   */
  public BatchSendResultDTO(
      int totalCount, int successCount, int failureCount, List<ReceiverSendResult> details) {
    this.totalCount = totalCount;
    this.successCount = successCount;
    this.failureCount = failureCount;
    this.details =
        details != null
            ? Collections.unmodifiableList(new ArrayList<>(details))
            : Collections.emptyList();
  }

  /**
   * 获取接收者总数
   *
   * @return 总数
   */
  public int getTotalCount() {
    return totalCount;
  }

  /**
   * 获取成功数
   *
   * @return 成功数
   */
  public int getSuccessCount() {
    return successCount;
  }

  /**
   * 获取失败数
   *
   * @return 失败数
   */
  public int getFailureCount() {
    return failureCount;
  }

  /**
   * 获取各接收者发送明细
   *
   * @return 不可变的发送明细列表
   */
  public List<ReceiverSendResult> getDetails() {
    return details;
  }

  /**
   * 是否全部成功
   *
   * @return 全部成功返回 true
   */
  public boolean isAllSuccess() {
    return failureCount == 0;
  }

  /**
   * 是否全部失败
   *
   * @return 全部失败返回 true
   */
  public boolean isAllFailed() {
    return successCount == 0;
  }

  /**
   * 获取失败接收者列表
   *
   * @return 失败接收者标识列表
   */
  public List<String> getFailedReceivers() {
    return details.stream()
        .filter(d -> !d.result().isSuccess())
        .map(ReceiverSendResult::receiver)
        .toList();
  }

  /**
   * 单条接收者发送结果记录
   *
   * @param receiver 接收者标识
   * @param result 发送结果
   */
  public record ReceiverSendResult(String receiver, NotifySendResult result) {}
}
