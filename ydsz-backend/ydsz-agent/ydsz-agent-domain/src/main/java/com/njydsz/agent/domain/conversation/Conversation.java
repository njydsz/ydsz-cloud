package com.njydsz.agent.domain.conversation;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import com.njydsz.agent.domain.model.ChatMessage;
import com.njydsz.agent.domain.model.TokenUsage;

/**
 * 对话聚合根
 *
 * <p>管理一组连续的消息序列，支持追加消息、查询历史、Token 统计。
 * 一个对话代表用户与 Agent 之间的一次完整交互会话。
 *
 * <p><b>线程安全</b>：非线程安全。appendMessage 会修改内部消息列表与累计用量/更新时间，
 * 同一对话实例须由单线程顺序写入或由外部同步（如按 conversationId 加锁）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class Conversation {

    private final String id;
    private final String userId;
    private final String agentId;
    private final String title;
    private final LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private final List<ChatMessage> messages;
    private TokenUsage totalUsage;

    public Conversation(String id, String userId, String agentId, String title) {
        this.id = Objects.requireNonNull(id, "id 不能为 null");
        this.userId = userId;
        this.agentId = agentId;
        this.title = title != null ? title : "新对话";
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
        this.messages = new ArrayList<>();
        this.totalUsage = TokenUsage.zero();
    }

    public Conversation(String id, String userId, String agentId, String title,
                        LocalDateTime createdAt, LocalDateTime updatedAt,
                        List<ChatMessage> messages, TokenUsage totalUsage) {
        this.id = id;
        this.userId = userId;
        this.agentId = agentId;
        this.title = title;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.messages = new ArrayList<>(messages);
        this.totalUsage = totalUsage != null ? totalUsage : TokenUsage.zero();
    }

    public void appendMessage(ChatMessage message) {
        Objects.requireNonNull(message, "message 不能为 null");
        messages.add(message);
        // 累计本轮 Token 消耗（不可变对象相加返回新实例），用于对话级用量统计与配额控制
        if (message.getTokenUsage() != null) {
            totalUsage = totalUsage.add(message.getTokenUsage());
        }
        updatedAt = LocalDateTime.now();
    }

    public List<ChatMessage> getMessages() {
        return Collections.unmodifiableList(messages);
    }

    public List<ChatMessage> getRecentMessages(int count) {
        if (messages.size() <= count) {
            return Collections.unmodifiableList(messages);
        }
        // 滑动窗口：仅截取最近 count 条，控制发送给 LLM 的上下文长度，避免长对话撑爆 Token 预算
        return Collections.unmodifiableList(
                messages.subList(messages.size() - count, messages.size()));
    }

    public ChatMessage getLastMessage() {
        return messages.isEmpty() ? null : messages.get(messages.size() - 1);
    }

    public int getMessageCount() {
        return messages.size();
    }

    public String getId() { return id; }
    public String getUserId() { return userId; }
    public String getAgentId() { return agentId; }
    public String getTitle() { return title; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public TokenUsage getTotalUsage() { return totalUsage; }

    @Override
    public String toString() {
        return "Conversation{id='" + id + "', title='" + title + "', messages=" + messages.size() + "}";
    }
}
