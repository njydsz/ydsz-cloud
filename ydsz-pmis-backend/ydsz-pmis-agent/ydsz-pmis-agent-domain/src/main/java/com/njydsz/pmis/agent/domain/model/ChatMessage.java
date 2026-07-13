package com.njydsz.pmis.agent.domain.model;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 对话消息值对象（对标 OpenAI Chat Completions message）
 *
 * <p>每条消息包含角色（System/User/Assistant/Tool）、内容、可选的工具调用信息。
 * 不可变值对象，修改操作返回新实例。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public final class ChatMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String id;
    private final MessageRole role;
    private final String content;
    private final String conversationId;
    private final LocalDateTime createdAt;
    private final List<ToolCall> toolCalls;
    private final String toolCallId;
    private final TokenUsage tokenUsage;

    public ChatMessage(String id, MessageRole role, String content, String conversationId,
                       LocalDateTime createdAt, List<ToolCall> toolCalls,
                       String toolCallId, TokenUsage tokenUsage) {
        this.id = Objects.requireNonNull(id, "id 不能为 null");
        this.role = Objects.requireNonNull(role, "role 不能为 null");
        this.content = content;
        this.conversationId = conversationId;
        this.createdAt = createdAt != null ? createdAt : LocalDateTime.now();
        this.toolCalls = toolCalls != null ? List.copyOf(toolCalls) : List.of();
        this.toolCallId = toolCallId;
        this.tokenUsage = tokenUsage;
    }

    public static ChatMessage system(String content) {
        return new ChatMessage(java.util.UUID.randomUUID().toString(),
                MessageRole.SYSTEM, content, null, LocalDateTime.now(), null, null, null);
    }

    public static ChatMessage user(String content, String conversationId) {
        return new ChatMessage(java.util.UUID.randomUUID().toString(),
                MessageRole.USER, content, conversationId, LocalDateTime.now(), null, null, null);
    }

    public static ChatMessage assistant(String content, String conversationId, TokenUsage usage) {
        return new ChatMessage(java.util.UUID.randomUUID().toString(),
                MessageRole.ASSISTANT, content, conversationId, LocalDateTime.now(), null, null, usage);
    }

    public static ChatMessage assistantWithTools(String content, String conversationId,
                                                  List<ToolCall> toolCalls, TokenUsage usage) {
        return new ChatMessage(java.util.UUID.randomUUID().toString(),
                MessageRole.ASSISTANT, content, conversationId, LocalDateTime.now(),
                toolCalls, null, usage);
    }

    public static ChatMessage tool(String toolCallId, String content, String conversationId) {
        return new ChatMessage(java.util.UUID.randomUUID().toString(),
                MessageRole.TOOL, content, conversationId, LocalDateTime.now(), null, toolCallId, null);
    }

    public String getId() { return id; }
    public MessageRole getRole() { return role; }
    public String getContent() { return content; }
    public String getConversationId() { return conversationId; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public List<ToolCall> getToolCalls() { return toolCalls; }
    public String getToolCallId() { return toolCallId; }
    public TokenUsage getTokenUsage() { return tokenUsage; }

    public boolean hasToolCalls() {
        return !toolCalls.isEmpty();
    }

    public ChatMessage appendContent(String delta) {
        String newContent = (content == null ? "" : content) + delta;
        return new ChatMessage(id, role, newContent, conversationId, createdAt,
                new ArrayList<>(toolCalls), toolCallId, tokenUsage);
    }

    @Override
    public String toString() {
        return "ChatMessage{role=" + role + ", content='" +
                (content != null && content.length() > 100 ? content.substring(0, 100) + "..." : content) + "'}";
    }
}
