package com.remisoft.agent.domain.conversation;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import com.remisoft.agent.domain.model.ChatMessage;
import com.remisoft.agent.domain.model.TokenUsage;

/**
 * 对话聚合根
 *
 * <p>管理一组连续的消息序列，支持追加消息、查询历史、Token 统计。
 * 一个对话代表用户与 Agent 之间的一次完整交互会话。
 *
 * <p><b>线程安全</b>：非线程安全。appendMessage 会修改内部消息列表与累计用量/更新时间，
 * 同一对话实例须由单线程顺序写入或由外部同步（如按 conversationId 加锁）。
 *
 * @author remi-team
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

    /**
     * 向对话尾部追加一条消息，并同步累计 Token 用量与更新时间。
     *
     * <p>聚合根不变量：消息严格按追加顺序排列，不支持插入或删除，保证上下文时序与 LLM 侧一致。
     * 消息自带 {@code tokenUsage} 时会累加到对话级 {@code totalUsage}，供配额控制与成本核算使用；
     * 未携带用量（如本地构造的占位消息）则只入列不计费。
     *
     * <p><b>并发</b>：非线程安全，同一 conversationId 的写入需由上层串行化。
     *
     * @param message 待追加的消息，不可为 {@code null}
     * @throws NullPointerException 当 {@code message} 为 {@code null} 时抛出
     */
    public void appendMessage(ChatMessage message) {
        Objects.requireNonNull(message, "message 不能为 null");
        messages.add(message);
        // 累计本轮 Token 消耗（不可变对象相加返回新实例），用于对话级用量统计与配额控制
        if (message.getTokenUsage() != null) {
            totalUsage = totalUsage.add(message.getTokenUsage());
        }
        updatedAt = LocalDateTime.now();
    }

    /**
     * 获取全部消息列表。
     *
     * <p>返回不可修改视图，调用方无法通过此引用改动对话内部状态，
     * 保证聚合根不变量的稳定性。</p>
     *
     * @return 全部消息的不可修改列表；空对话返回空列表而非 {@code null}
     */
    public List<ChatMessage> getMessages() {
        return Collections.unmodifiableList(messages);
    }

    /**
     * 获取最近的 N 条消息。
     *
     * <p>当消息总数不超过 {@code count} 时返回全部；否则截取最后 {@code count} 条。
     * 用于控制发送给 LLM 的上下文窗口大小，避免长对话超出 Token 预算。</p>
     *
     * @param count 需要获取的最近消息条数，须为非负整数
     * @return 最近消息的不可修改列表（最多 count 条），空对话返回空列表
     * @throws IllegalArgumentException 当 {@code count} 为负数时抛出
     */
    public List<ChatMessage> getRecentMessages(int count) {
        if (messages.size() <= count) {
            return Collections.unmodifiableList(messages);
        }
        // 滑动窗口：仅截取最近 count 条，控制发送给 LLM 的上下文长度，避免长对话撑爆 Token 预算
        return Collections.unmodifiableList(
                messages.subList(messages.size() - count, messages.size()));
    }

    /**
     * 获取最后一条消息。
     *
     * @return 最后追加的消息；对话为空时返回 {@code null}
     */
    public ChatMessage getLastMessage() {
        return messages.isEmpty() ? null : messages.get(messages.size() - 1);
    }

    /**
     * 获取消息总数。
     *
     * @return 对话中的消息条数（含用户与助手消息）
     */
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
