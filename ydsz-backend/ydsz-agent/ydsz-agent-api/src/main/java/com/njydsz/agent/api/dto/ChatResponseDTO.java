package com.njydsz.agent.api.dto;

import java.io.Serializable;
import java.time.LocalDateTime;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 对话响应 DTO
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Schema(description = "对话响应")
public class ChatResponseDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @Schema(description = "对话 ID")
    private String conversationId;

    @Schema(description = "助手回复内容")
    private String content;

    @Schema(description = "模型名称")
    private String model;

    @Schema(description = "Token 用量")
    private TokenUsageDTO usage;

    @Schema(description = "响应时间")
    private LocalDateTime respondedAt;

    public ChatResponseDTO() {
    }

    public ChatResponseDTO(String conversationId, String content, String model,
                           TokenUsageDTO usage, LocalDateTime respondedAt) {
        this.conversationId = conversationId;
        this.content = content;
        this.model = model;
        this.usage = usage;
        this.respondedAt = respondedAt;
    }

    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public TokenUsageDTO getUsage() { return usage; }
    public void setUsage(TokenUsageDTO usage) { this.usage = usage; }
    public LocalDateTime getRespondedAt() { return respondedAt; }
    public void setRespondedAt(LocalDateTime respondedAt) { this.respondedAt = respondedAt; }

    public static class TokenUsageDTO implements Serializable {
        private static final long serialVersionUID = 1L;

        @Schema(description = "输入 Token")
        private int promptTokens;
        @Schema(description = "输出 Token")
        private int completionTokens;
        @Schema(description = "总 Token")
        private int totalTokens;

        public TokenUsageDTO() {
        }

        public TokenUsageDTO(int promptTokens, int completionTokens, int totalTokens) {
            this.promptTokens = promptTokens;
            this.completionTokens = completionTokens;
            this.totalTokens = totalTokens;
        }

        public int getPromptTokens() { return promptTokens; }
        public void setPromptTokens(int promptTokens) { this.promptTokens = promptTokens; }
        public int getCompletionTokens() { return completionTokens; }
        public void setCompletionTokens(int completionTokens) { this.completionTokens = completionTokens; }
        public int getTotalTokens() { return totalTokens; }
        public void setTotalTokens(int totalTokens) { this.totalTokens = totalTokens; }
    }
}
