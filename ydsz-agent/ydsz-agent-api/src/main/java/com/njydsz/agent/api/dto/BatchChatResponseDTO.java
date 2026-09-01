package com.njydsz.agent.api.dto;

import java.io.Serializable;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 批量对话响应 DTO
 *
 * <p>封装一组对话结果，与请求条目一一对应。
 *
 * <p>每条结果独立返回成功或失败：成功的包含 content / usage，失败的包含 errorMessage。
 * 调用方可通过 {@link BatchResultItem#success} 字段快速判断每条结果状态。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Schema(description = "批量对话响应")
public class BatchChatResponseDTO implements Serializable {

  private static final long serialVersionUID = 1L;

  /** 批量结果列表（与请求 items 顺序一致） */
  @Schema(description = "批量结果列表（与请求 items 顺序一致）")
  private List<BatchResultItem> results;

  /** 总耗时（毫秒） */
  @Schema(description = "总耗时（毫秒）")
  private long totalDurationMs;

  /** 成功条目数 */
  @Schema(description = "成功条目数")
  private int successCount;

  /** 失败条目数 */
  @Schema(description = "失败条目数")
  private int failedCount;

  public List<BatchResultItem> getResults() {
    return results;
  }

  public void setResults(List<BatchResultItem> results) {
    this.results = results;
  }

  public long getTotalDurationMs() {
    return totalDurationMs;
  }

  public void setTotalDurationMs(long totalDurationMs) {
    this.totalDurationMs = totalDurationMs;
  }

  public int getSuccessCount() {
    return successCount;
  }

  public void setSuccessCount(int successCount) {
    this.successCount = successCount;
  }

  public int getFailedCount() {
    return failedCount;
  }

  public void setFailedCount(int failedCount) {
    this.failedCount = failedCount;
  }

  /**
   * 批量对话单条结果
   *
   * <p>每条结果与请求中的 {@link BatchChatRequestDTO.BatchChatItem} 通过 {@link #itemId} 对应。
   */
  @Schema(description = "批量对话单条结果")
  public static class BatchResultItem implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 条目标识（与请求中 itemId 对应） */
    @Schema(description = "条目标识（与请求中 itemId 对应）")
    private String itemId;

    /** 是否成功 */
    @Schema(description = "是否成功")
    private boolean success;

    /** 响应内容（成功时非空） */
    @Schema(description = "响应内容（成功时非空）")
    private String content;

    /** 使用模型（成功时非空） */
    @Schema(description = "使用模型（成功时非空）")
    private String model;

    /** Token 用量（成功时非空） */
    @Schema(description = "Token 用量")
    private ChatResponseDTO.TokenUsageDTO usage;

    /** 结束原因（成功时非空） */
    @Schema(description = "结束原因")
    private String finishReason;

    /** 错误信息（失败时非空） */
    @Schema(description = "错误信息（失败时非空）")
    private String errorMessage;

    public String getItemId() {
      return itemId;
    }

    public void setItemId(String itemId) {
      this.itemId = itemId;
    }

    public boolean isSuccess() {
      return success;
    }

    public void setSuccess(boolean success) {
      this.success = success;
    }

    public String getContent() {
      return content;
    }

    public void setContent(String content) {
      this.content = content;
    }

    public String getModel() {
      return model;
    }

    public void setModel(String model) {
      this.model = model;
    }

    public ChatResponseDTO.TokenUsageDTO getUsage() {
      return usage;
    }

    public void setUsage(ChatResponseDTO.TokenUsageDTO usage) {
      this.usage = usage;
    }

    public String getFinishReason() {
      return finishReason;
    }

    public void setFinishReason(String finishReason) {
      this.finishReason = finishReason;
    }

    public String getErrorMessage() {
      return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
      this.errorMessage = errorMessage;
    }
  }
}
