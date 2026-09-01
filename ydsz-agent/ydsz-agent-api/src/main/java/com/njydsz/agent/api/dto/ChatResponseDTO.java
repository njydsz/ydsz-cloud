package com.njydsz.agent.api.dto;

import java.io.Serializable;
import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 对话响应 DTO
 *
 * <p>封装 Agent 对话的响应结果，包括回复内容、 实际使用的模型、Token 用量统计和响应时间。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Schema(description = "对话响应")
public class ChatResponseDTO implements Serializable {

  private static final long serialVersionUID = 1L;

  /** 对话 ID */
  @Schema(description = "对话 ID")
  private String conversationId;

  /** 助手回复内容 */
  @Schema(description = "助手回复内容")
  private String content;

  /** 实际使用的模型名称 */
  @Schema(description = "模型名称")
  private String model;

  /** Token 用量统计 */
  @Schema(description = "Token 用量")
  private TokenUsageDTO usage;

  /** 响应时间 */
  @Schema(description = "响应时间")
  private LocalDateTime respondedAt;

  public ChatResponseDTO() {}

  public ChatResponseDTO(
      String conversationId,
      String content,
      String model,
      TokenUsageDTO usage,
      LocalDateTime respondedAt) {
    this.conversationId = conversationId;
    this.content = content;
    this.model = model;
    this.usage = usage;
    this.respondedAt = respondedAt;
  }

  public String getConversationId() {
    return conversationId;
  }

  public void setConversationId(String conversationId) {
    this.conversationId = conversationId;
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

  public TokenUsageDTO getUsage() {
    return usage;
  }

  public void setUsage(TokenUsageDTO usage) {
    this.usage = usage;
  }

  public LocalDateTime getRespondedAt() {
    return respondedAt;
  }

  public void setRespondedAt(LocalDateTime respondedAt) {
    this.respondedAt = respondedAt;
  }

  /**
   * Token 用量统计 DTO
   *
   * <p>记录单次 LLM 调用的 Token 消耗明细，用于成本分析和用量监控。
   *
   * @author ydsz-team
   * @since 26.09.01
   */
  public static class TokenUsageDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 输入 Token 数量（prompt 消耗） */
    @Schema(description = "输入 Token")
    private int promptTokens;

    /** 输出 Token 数量（completion 消耗） */
    @Schema(description = "输出 Token")
    private int completionTokens;

    /** 总 Token 数量（prompt + completion） */
    @Schema(description = "总 Token")
    private int totalTokens;

    public TokenUsageDTO() {}

    public TokenUsageDTO(int promptTokens, int completionTokens, int totalTokens) {
      this.promptTokens = promptTokens;
      this.completionTokens = completionTokens;
      this.totalTokens = totalTokens;
    }

    public int getPromptTokens() {
      return promptTokens;
    }

    public void setPromptTokens(int promptTokens) {
      this.promptTokens = promptTokens;
    }

    public int getCompletionTokens() {
      return completionTokens;
    }

    public void setCompletionTokens(int completionTokens) {
      this.completionTokens = completionTokens;
    }

    public int getTotalTokens() {
      return totalTokens;
    }

    public void setTotalTokens(int totalTokens) {
      this.totalTokens = totalTokens;
    }
  }
}
