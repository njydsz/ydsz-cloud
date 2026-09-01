package com.njydsz.agent.domain.model;

import java.io.Serializable;
import java.util.List;

/**
 * 批量对话结果（领域模型）
 *
 * <p>封装一组并行对话的结果，每条结果独立标记成功/失败。
 *
 * <p>设计要点：
 *
 * <ul>
 *   <li>单条失败不影响其他条目（隔离性）
 *   <li>结果顺序与请求条目顺序一致（可预测性）
 *   <li>包含总耗时和成功/失败计数（可观测性）
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class BatchChatResult implements Serializable {

  private static final long serialVersionUID = 1L;

  /** 批量结果列表（与请求 items 顺序一致） */
  private final List<BatchResultItem> results;

  /** 总耗时（毫秒） */
  private final long totalDurationMs;

  /** 成功条目数 */
  private final int successCount;

  /** 失败条目数 */
  private final int failedCount;

  /**
   * 构造批量对话结果，并自动统计成功/失败条目数。
   *
   * @param results 批量结果列表（与请求 items 顺序一致）
   * @param totalDurationMs 总耗时（毫秒）
   */
  public BatchChatResult(List<BatchResultItem> results, long totalDurationMs) {
    this.results = results;
    this.totalDurationMs = totalDurationMs;
    int success = 0;
    int failed = 0;
    for (BatchResultItem item : results) {
      if (item.isSuccess()) {
        success++;
      } else {
        failed++;
      }
    }
    this.successCount = success;
    this.failedCount = failed;
  }

  /**
   * 获取批量结果列表。
   *
   * @return 批量结果列表（与请求 items 顺序一致）
   */
  public List<BatchResultItem> getResults() {
    return results;
  }

  /**
   * 获取总耗时。
   *
   * @return 总耗时（毫秒）
   */
  public long getTotalDurationMs() {
    return totalDurationMs;
  }

  /**
   * 获取成功条目数。
   *
   * @return 成功条目数
   */
  public int getSuccessCount() {
    return successCount;
  }

  /**
   * 获取失败条目数。
   *
   * @return 失败条目数
   */
  public int getFailedCount() {
    return failedCount;
  }

  /**
   * 批量对话单条结果
   *
   * <p>不可变对象，通过 {@link #success} 标记本条对话是否成功完成。
   */
  public static class BatchResultItem implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 条目标识（与请求中 itemId 对应） */
    private final String itemId;

    /** 是否成功 */
    private final boolean success;

    /** 响应内容（成功时非空） */
    private final String content;

    /** 使用模型（成功时非空） */
    private final String model;

    /** Token 用量（成功时非空） */
    private final TokenUsage usage;

    /** 结束原因（成功时非空） */
    private final String finishReason;

    /** 错误信息（失败时非空） */
    private final String errorMessage;

    private BatchResultItem(
        String itemId,
        boolean success,
        String content,
        String model,
        TokenUsage usage,
        String finishReason,
        String errorMessage) {
      this.itemId = itemId;
      this.success = success;
      this.content = content;
      this.model = model;
      this.usage = usage;
      this.finishReason = finishReason;
      this.errorMessage = errorMessage;
    }

    /** 创建成功结果 */
    public static BatchResultItem success(
        String itemId, String content, String model, TokenUsage usage, String finishReason) {
      return new BatchResultItem(itemId, true, content, model, usage, finishReason, null);
    }

    /** 创建失败结果 */
    public static BatchResultItem failure(String itemId, String errorMessage) {
      return new BatchResultItem(itemId, false, null, null, null, null, errorMessage);
    }

    public String getItemId() {
      return itemId;
    }

    public boolean isSuccess() {
      return success;
    }

    public String getContent() {
      return content;
    }

    public String getModel() {
      return model;
    }

    public TokenUsage getUsage() {
      return usage;
    }

    public String getFinishReason() {
      return finishReason;
    }

    public String getErrorMessage() {
      return errorMessage;
    }
  }
}
