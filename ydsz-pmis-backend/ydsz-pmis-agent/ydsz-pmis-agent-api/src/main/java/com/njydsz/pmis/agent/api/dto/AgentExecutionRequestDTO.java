package com.njydsz.pmis.agent.api.dto;

import java.io.Serializable;
import java.util.List;

import jakarta.validation.constraints.NotBlank;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Agent 执行请求 DTO
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Schema(description = "Agent 执行请求")
public class AgentExecutionRequestDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "Agent 编码（指定使用哪个 Agent）")
    private String agentCode;

    @Schema(description = "对话 ID（null 表示新建对话）")
    private String conversationId;

    @NotBlank(message = "用户输入不能为空")
    @Schema(description = "用户输入", requiredMode = Schema.RequiredMode.REQUIRED)
    private String userInput;

    @Schema(description = "系统提示词（可选，覆盖默认）")
    private String systemPrompt;

    @Schema(description = "最大迭代次数（ReAct 模式）")
    private Integer maxIterations;

    @Schema(description = "启用的工具列表（可选）")
    private List<String> enabledTools;

    @Schema(description = "是否流式输出")
    private boolean stream;

    public String getAgentCode() { return agentCode; }
    public void setAgentCode(String agentCode) { this.agentCode = agentCode; }
    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }
    public String getUserInput() { return userInput; }
    public void setUserInput(String userInput) { this.userInput = userInput; }
    public String getSystemPrompt() { return systemPrompt; }
    public void setSystemPrompt(String systemPrompt) { this.systemPrompt = systemPrompt; }
    public Integer getMaxIterations() { return maxIterations; }
    public void setMaxIterations(Integer maxIterations) { this.maxIterations = maxIterations; }
    public List<String> getEnabledTools() { return enabledTools; }
    public void setEnabledTools(List<String> enabledTools) { this.enabledTools = enabledTools; }
    public boolean isStream() { return stream; }
    public void setStream(boolean stream) { this.stream = stream; }
}
