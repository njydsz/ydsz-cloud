package com.remisoft.agent.api.dto;

import java.io.Serializable;

import jakarta.validation.constraints.NotBlank;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 对话请求 DTO
 *
 * <p>封装用户与 Agent 进行单轮/多轮对话的请求参数，
 * 包括消息内容、模型选择、生成参数等。
 *
 * @author remi-team
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

    /** 用户消息内容（必填） */
    @NotBlank(message = "消息内容不能为空")
    @Schema(description = "用户消息", requiredMode = Schema.RequiredMode.REQUIRED)
    private String message;

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

    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }
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
