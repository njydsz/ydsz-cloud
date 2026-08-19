package com.njydsz.agent.api.dto;

import java.io.Serializable;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

/**
 * 批量对话请求 DTO
 *
 * <p>封装一组对话请求，由服务端并行调用 LLM 后统一返回结果。
 *
 * <p>适用场景：
 *
 * <ul>
 *   <li>批量问答（离线评估、数据集标注）
 *   <li>多 Prompt 对比测试（A/B 测试不同 systemPrompt 效果）
 *   <li>批量内容生成（标题生成、摘要生成等）
 * </ul>
 *
 * <p>限制：
 *
 * <ul>
 *   <li>单次最多 50 条（避免单次请求占用过久）
 *   <li>所有请求共享同一模型配置（model / temperature / maxTokens）
 *   <li>每条请求独立对话 ID，互不干扰
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Schema(description = "批量对话请求")
public class BatchChatRequestDTO implements Serializable {

  private static final long serialVersionUID = 1L;

  /** 批量对话条目列表（至少 1 条，最多 50 条） */
  @NotEmpty(message = "批量对话条目不能为空")
  @Size(min = 1, max = 50, message = "批量对话条目数必须在 1-50 之间")
  @Schema(description = "批量对话条目列表（1-50 条）")
  private List<BatchChatItem> items;

  /** 模型名称（可选，覆盖默认模型配置） */
  @Schema(description = "模型名称（可选，覆盖默认）")
  private String model;

  /** 温度参数（可选，取值范围 0-2） */
  @Schema(description = "温度（可选，0-2）")
  private Double temperature;

  /** 最大生成 Token 数（可选） */
  @Schema(description = "最大 Token（可选）")
  private Integer maxTokens;

  /** 系统提示词（可选，所有条目共享） */
  @Schema(description = "系统提示词（可选，所有条目共享）")
  private String systemPrompt;

  public List<BatchChatItem> getItems() {
    return items;
  }

  public void setItems(List<BatchChatItem> items) {
    this.items = items;
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

  public String getSystemPrompt() {
    return systemPrompt;
  }

  public void setSystemPrompt(String systemPrompt) {
    this.systemPrompt = systemPrompt;
  }

  /**
   * 批量对话单条条目
   *
   * <p>每条包含独立的用户消息和对话 ID，共享外层模型配置。
   */
  @Schema(description = "批量对话单条条目")
  public static class BatchChatItem implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 条目唯一标识（用于响应中对应结果，由调用方保证唯一） */
    @Schema(description = "条目唯一标识（用于响应匹配）")
    private String itemId;

    /** 对话 ID（null 则新建） */
    @Schema(description = "对话 ID（null 则新建）")
    private String conversationId;

    /** 用户消息（纯文本，与 multimodalContent 二选一） */
    @Schema(description = "用户消息（纯文本，与 multimodalContent 二选一）")
    private String message;

    /** 多模态内容段落（Vision 模型，与 message 二选一） */
    @Schema(description = "多模态内容段落（Vision 模型，与 message 二选一）")
    private List<ChatRequestDTO.ContentPartDTO> multimodalContent;

    /** 系统提示词（可选，覆盖外层共享 systemPrompt） */
    @Schema(description = "系统提示词（可选，覆盖外层共享）")
    private String systemPrompt;

    public String getItemId() {
      return itemId;
    }

    public void setItemId(String itemId) {
      this.itemId = itemId;
    }

    public String getConversationId() {
      return conversationId;
    }

    public void setConversationId(String conversationId) {
      this.conversationId = conversationId;
    }

    public String getMessage() {
      return message;
    }

    public void setMessage(String message) {
      this.message = message;
    }

    public List<ChatRequestDTO.ContentPartDTO> getMultimodalContent() {
      return multimodalContent;
    }

    public void setMultimodalContent(List<ChatRequestDTO.ContentPartDTO> multimodalContent) {
      this.multimodalContent = multimodalContent;
    }

    public String getSystemPrompt() {
      return systemPrompt;
    }

    public void setSystemPrompt(String systemPrompt) {
      this.systemPrompt = systemPrompt;
    }
  }
}
