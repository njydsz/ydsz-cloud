package com.njydsz.pmis.agent.api.dto;

import java.io.Serializable;

import jakarta.validation.constraints.NotBlank;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 对话请求 DTO
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Schema(description = "对话请求")
public class ChatRequestDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "对话 ID（null 表示新建对话）")
    private String conversationId;

    @NotBlank(message = "消息内容不能为空")
    @Schema(description = "用户消息", requiredMode = Schema.RequiredMode.REQUIRED)
    private String message;

    @Schema(description = "系统提示词（可选，覆盖默认）")
    private String systemPrompt;

    @Schema(description = "模型名称（可选，覆盖默认）")
    private String model;

    @Schema(description = "温度（可选，0-2）")
    private Double temperature;

    @Schema(description = "最大 Token（可选）")
    private Integer maxTokens;

    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getSystemPrompt() { return systemPrompt; }
    public void setSystemPrompt(String systemPrompt) { this.systemPrompt = systemPrompt; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public Double getTemperature() { return temperature; }
    public void setTemperature(Double temperature) { this.temperature = temperature; }
    public Integer getMaxTokens() { return maxTokens; }
    public void setMaxTokens(Integer maxTokens) { this.maxTokens = maxTokens; }
}
