package com.njydsz.agent.domain.dto;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

import com.njydsz.common.json.annotation.JsonView;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 对话响应 DTO
 *
 * <p>封装 Agent 对话的响应结果，包括回复内容、 实际使用的模型、Token 用量统计和响应时间。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
@AllArgsConstructor
@Schema(description = "对话响应")
public class ChatResponseDTO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 对话 ID */
  @JsonView(ChatResponseViews.Summary.class)
  @Schema(description = "对话 ID")
  private String conversationId;

  /** 助手回复内容 */
  @JsonView(ChatResponseViews.Summary.class)
  @Schema(description = "助手回复内容")
  private String content;

  /** 实际使用的模型名称 */
  @JsonView(ChatResponseViews.Detail.class)
  @Schema(description = "模型名称")
  private String model;

  /** Token 用量统计 */
  @JsonView(ChatResponseViews.Detail.class)
  @Schema(description = "Token 用量")
  private TokenUsageDTO usage;

  /** 响应时间 */
  @JsonView(ChatResponseViews.Summary.class)
  @Schema(description = "响应时间")
  private LocalDateTime respondedAt;

  public ChatResponseDTO() {}

  /**
   * Token 用量统计 DTO
   *
   * <p>记录单次 LLM 调用的 Token 消耗明细，用于成本分析和用量监控。
   *
   * @author ydsz-team
   * @since 26.09.01
   */
  @Data
  @AllArgsConstructor
  public static class TokenUsageDTO implements Serializable {
    @Serial private static final long serialVersionUID = 1L;

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
  }
}
