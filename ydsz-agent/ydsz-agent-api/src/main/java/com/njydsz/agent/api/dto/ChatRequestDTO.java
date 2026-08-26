package com.njydsz.agent.api.dto;

import java.io.Serializable;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 对话请求 DTO
 *
 * <p>封装用户与 Agent 进行单轮/多轮对话的请求参数， 包括消息内容、模型选择、生成参数等。
 *
 * <p>支持两种消息输入方式：
 *
 * <ul>
 *   <li>纯文本：通过 {@link #message} 字段传入（向后兼容）
 *   <li>多模态（Vision 模型）：通过 {@link #multimodalContent} 传入内容段落列表（文本+图片）
 * </ul>
 *
 * <p>当 {@code multimodalContent} 非空时，优先使用多模态格式，{@code message} 忽略。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Schema(description = "对话请求")
public class ChatRequestDTO implements Serializable {

  private static final long serialVersionUID = 1L;

  /** 对话 ID（null 表示新建对话，非 null 表示续接已有对话） */
  @Schema(description = "对话 ID（null 表示新建对话）")
  private String conversationId;

  /** 请求幂等键（可选，防止重复调用 LLM 扣费） */
  @Schema(description = "请求幂等键（可选，防止重复调用 LLM 扣费）")
  private String requestId;

  /** 用户消息内容（必填，纯文本场景） */
  @NotBlank(message = "消息内容不能为空")
  @Schema(description = "用户消息（纯文本，与 multimodalContent 二选一）")
  private String message;

  /**
   * 多模态内容段落列表（Vision 模型场景，与 message 二选一）
   *
   * <p>当该字段非空时，优先使用多模态格式传递给 LLM，{@code message} 字段忽略。 每个段落可以是文本（type=text）或图片（type=image_url）。
   */
  @Schema(description = "多模态内容段落（Vision 模型，与 message 二选一）")
  private List<ContentPartDTO> multimodalContent;

  /** 系统提示词（可选，覆盖 Agent 默认配置） */
  @Schema(description = "系统提示词（可选，覆盖默认）")
  private String systemPrompt;

  /** 模型名称（可选，覆盖默认模型配置） */
  @Schema(description = "模型名称（可选，覆盖默认）")
  private String model;

  /** 温度参数（可选，取值范围 0-2，值越大生成越随机） */
  @Schema(description = "温度（可选，0-2）")
  private Double temperature;

  /** 最大生成 Token 数（可选） */
  @Schema(description = "最大 Token（可选）")
  private Integer maxTokens;

  public String getConversationId() {
    return conversationId;
  }

  public void setConversationId(String conversationId) {
    this.conversationId = conversationId;
  }

  public String getRequestId() {
    return requestId;
  }

  public void setRequestId(String requestId) {
    this.requestId = requestId;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  public List<ContentPartDTO> getMultimodalContent() {
    return multimodalContent;
  }

  public void setMultimodalContent(List<ContentPartDTO> multimodalContent) {
    this.multimodalContent = multimodalContent;
  }

  public String getSystemPrompt() {
    return systemPrompt;
  }

  public void setSystemPrompt(String systemPrompt) {
    this.systemPrompt = systemPrompt;
  }

  public String getModel() {
    return model;
  }

  public void setModel(String model) {
    this.model = model;
  }

  public Double getTemperature() {
    return temperature;
  }

  public void setTemperature(Double temperature) {
    this.temperature = temperature;
  }

  public Integer getMaxTokens() {
    return maxTokens;
  }

  public void setMaxTokens(Integer maxTokens) {
    this.maxTokens = maxTokens;
  }

  /**
   * 多模态内容段落 DTO
   *
   * <p>每个段落可以是文本或图片之一，类型由 {@link #type} 标识。
   */
  @Schema(description = "多模态内容段落")
  public static class ContentPartDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 内容类型（text / image_url） */
    @Schema(description = "内容类型（text / image_url）")
    private String type;

    /** 文本内容（type=text 时有效） */
    @Schema(description = "文本内容（type=text 时有效）")
    private String text;

    /** 图片 URL（type=image_url 时有效，支持 http(s):// 或 data:image/... 内联格式） */
    @Schema(description = "图片 URL（type=image_url 时有效）")
    private String imageUrl;

    public String getType() {
      return type;
    }

    public void setType(String type) {
      this.type = type;
    }

    public String getText() {
      return text;
    }

    public void setText(String text) {
      this.text = text;
    }

    public String getImageUrl() {
      return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
      this.imageUrl = imageUrl;
    }
  }
}
