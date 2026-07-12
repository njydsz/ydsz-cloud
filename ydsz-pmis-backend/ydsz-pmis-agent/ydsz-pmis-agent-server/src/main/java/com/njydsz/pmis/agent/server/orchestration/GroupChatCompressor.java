package com.njydsz.pmis.agent.server.orchestration.strategy;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * GroupChat 对话压缩器（P2-5 落地）。
 *
 * <p>对标 Coze Multi-Agent 对话压缩 / Dify Conversation Memory Compression：
 * 当 GroupChat 多轮对话历史超过阈值时，自动压缩旧对话，
 * 保留关键信息，避免 Token 溢出。
 *
 * <p>压缩策略：
 * <ol>
 *   <li>当对话消息数超过 {@code maxMessages}（默认 20）时触发压缩</li>
 *   <li>保留最近 {@code keepRecent}（默认 6）条消息不压缩</li>
 *   <li>将旧消息合并为一条摘要消息</li>
 *   <li>摘要消息格式："[对话摘要] Agent A 提出了X，Agent B 补充了Y..."</li>
 * </ol>
 *
 * <p>与简单的截断（删除旧消息）相比，压缩保留了关键信息，
 * 避免因丢失上下文导致对话质量下降。
 *
 * @author ydsz-pmis-team
 * @since 1.5.0 (P2-5)
 */
@Slf4j
@Component
public class GroupChatCompressor {

    /** 默认最大消息数（超过触发压缩） */
    public static final int DEFAULT_MAX_MESSAGES = 20;

    /** 默认保留最近消息数（不压缩） */
    public static final int DEFAULT_KEEP_RECENT = 6;

    /**
     * 对话消息（简化结构）。
     */
    public record ChatMessage(String agentName, String content, long timestamp) {}

    /**
     * 压缩对话历史。
     *
     * @param messages    原始消息列表
     * @param maxMessages 最大消息数（超过则触发压缩）
     * @param keepRecent  保留最近消息数（不压缩）
     * @return 压缩后的消息列表
     */
    public List<ChatMessage> compress(List<ChatMessage> messages,
                                       int maxMessages, int keepRecent) {
        if (messages == null || messages.size() <= maxMessages) {
            return messages;
        }
        if (maxMessages <= 0) maxMessages = DEFAULT_MAX_MESSAGES;
        if (keepRecent <= 0) keepRecent = DEFAULT_KEEP_RECENT;
        if (keepRecent >= messages.size()) {
            return messages;
        }

        // 分割：旧消息（压缩） + 最近消息（保留）
        int splitIdx = messages.size() - keepRecent;
        List<ChatMessage> oldMessages = messages.subList(0, splitIdx);
        List<ChatMessage> recentMessages = messages.subList(splitIdx, messages.size());

        // 生成摘要
        String summary = generateSummary(oldMessages);

        // 构造压缩后的列表
        List<ChatMessage> compressed = new ArrayList<>();
        compressed.add(new ChatMessage("Moderator", summary, System.currentTimeMillis()));
        compressed.addAll(recentMessages);

        log.info("[GroupChatCompressor] 压缩: {} → {} 条消息 (摘要 {} 字)",
                messages.size(), compressed.size(), summary.length());
        return compressed;
    }

    /**
     * 自动压缩（使用默认参数）。
     */
    public List<ChatMessage> compress(List<ChatMessage> messages) {
        return compress(messages, DEFAULT_MAX_MESSAGES, DEFAULT_KEEP_RECENT);
    }

    /**
     * 检查是否需要压缩。
     */
    public boolean needsCompression(List<ChatMessage> messages) {
        return messages != null && messages.size() > DEFAULT_MAX_MESSAGES;
    }

    /**
     * 生成旧消息摘要。
     *
     * <p>当前使用基于规则的摘要（提取每个 Agent 的关键发言），
     * 后续可替换为 LLM 生成摘要。
     */
    private String generateSummary(List<ChatMessage> messages) {
        StringBuilder sb = new StringBuilder("[对话摘要] ");

        // 按 Agent 分组，提取每个 Agent 的最后一条发言
        java.util.LinkedHashMap<String, String> lastSpoke = new java.util.LinkedHashMap<>();
        for (ChatMessage msg : messages) {
            lastSpoke.put(msg.agentName(), truncate(msg.content(), 200));
        }

        int count = 0;
        for (var entry : lastSpoke.entrySet()) {
            if (count > 0) sb.append("；");
            sb.append(entry.getKey()).append("：").append(entry.getValue());
            count++;
            if (count >= 5) {
                sb.append("等");
                break;
            }
        }

        return sb.toString();
    }

    /**
     * 截断文本。
     */
    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() > maxLen ? text.substring(0, maxLen) + "..." : text;
    }
}
