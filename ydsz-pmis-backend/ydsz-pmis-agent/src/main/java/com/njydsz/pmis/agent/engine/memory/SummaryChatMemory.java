package com.njydsz.pmis.agent.engine.memory;

import com.njydsz.pmis.agent.engine.llm.LlmProvider;
import com.njydsz.pmis.agent.engine.llm.LlmProviderRouter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * 摘要对话记忆（P4-3 落地）。
 *
 * <p>对标 LangChain ConversationSummaryMemory / Coze 长记忆压缩：
 * <ul>
 *   <li>当对话历史超过阈值时，自动将较早的消息压缩为 LLM 生成的摘要</li>
 *   <li>摘要作为 SYSTEM 消息保留在历史头部，保留关键上下文信息</li>
 *   <li>近期消息保留完整内容，确保最新对话的精确性</li>
 *   <li>显著减少 token 消耗，支持更长的多轮对话</li>
 * </ul>
 *
 * <p>工作流程：
 * <pre>
 * [用户消息1] [助手回复1] [用户消息2] [助手回复2] ... [用户消息N] [助手回复N]
 *                              ↓ 当总 token > summaryThreshold 时触发压缩
 * [SYSTEM: 之前的对话摘要: 用户讨论了...] [用户消息N-1] [助手回复N-1] [用户消息N] [助手回复N]
 * </pre>
 *
 * <p>典型用法：
 * <pre>
 * SummaryChatMemory memory = new SummaryChatMemory(delegate, llmRouter, 2000, 5);
 * memory.addMessage(sessionId, ChatMessage.user("你好"));
 * List&lt;ChatMessage&gt; history = memory.getHistory(sessionId);
 * </pre>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P4-3)
 */
@Slf4j
public class SummaryChatMemory {

    /** 摘要触发阈值（总 token 数超过此值时触发压缩） */
    private static final int DEFAULT_SUMMARY_THRESHOLD = 2000;

    /** 压缩后保留的近期消息轮数 */
    private static final int DEFAULT_KEEP_RECENT_ROUNDS = 5;

    /** 摘要提示词模板 */
    private static final String SUMMARY_PROMPT = """
            请将以下对话历史压缩为一段简洁的摘要，保留关键信息（用户意图、已确认的结论、待解决的问题）。
            摘要应使用第三人称，不超过 300 字。

            对话历史：
            %s

            请输出摘要（不要包含其他内容）：
            """;

    private final ChatMemory delegate;
    private final LlmProviderRouter llmProviderRouter;
    private final int summaryThreshold;
    private final int keepRecentRounds;

    /**
     * 构造摘要对话记忆。
     *
     * @param delegate           底层记忆实现（内存版或 Redis 版）
     * @param llmProviderRouter  LLM 路由器（用于生成摘要）
     * @param summaryThreshold   摘要触发阈值（token 数）
     * @param keepRecentRounds   压缩后保留的近期消息轮数
     */
    public SummaryChatMemory(ChatMemory delegate, LlmProviderRouter llmProviderRouter,
                              int summaryThreshold, int keepRecentRounds) {
        this.delegate = delegate;
        this.llmProviderRouter = llmProviderRouter;
        this.summaryThreshold = summaryThreshold > 0 ? summaryThreshold : DEFAULT_SUMMARY_THRESHOLD;
        this.keepRecentRounds = keepRecentRounds > 0 ? keepRecentRounds : DEFAULT_KEEP_RECENT_ROUNDS;
        log.info("[SummaryChatMemory] 初始化, summaryThreshold={}, keepRecentRounds={}",
                this.summaryThreshold, this.keepRecentRounds);
    }

    /**
     * 使用默认配置构造。
     */
    public SummaryChatMemory(ChatMemory delegate, LlmProviderRouter llmProviderRouter) {
        this(delegate, llmProviderRouter, DEFAULT_SUMMARY_THRESHOLD, DEFAULT_KEEP_RECENT_ROUNDS);
    }

    /**
     * 添加消息并检查是否需要触发摘要压缩。
     */
    public void addMessage(String sessionId, ChatMessage message) {
        delegate.addMessage(sessionId, message);
        // 检查是否需要触发摘要压缩
        tryCompress(sessionId);
    }

    /**
     * 批量添加消息。
     */
    public void addMessages(String sessionId, List<ChatMessage> messages) {
        delegate.addMessages(sessionId, messages);
        tryCompress(sessionId);
    }

    /**
     * 获取对话历史（直接委托给底层实现）。
     */
    public List<ChatMessage> getHistory(String sessionId) {
        return delegate.getHistory(sessionId);
    }

    /**
     * 获取 token 总数。
     */
    public int getTokenCount(String sessionId) {
        return delegate.getTokenCount(sessionId);
    }

    /**
     * 获取消息数。
     */
    public int getMessageCount(String sessionId) {
        return delegate.getMessageCount(sessionId);
    }

    /**
     * 清除会话历史。
     */
    public void clear(String sessionId) {
        delegate.clear(sessionId);
    }

    /**
     * 尝试触发摘要压缩。
     *
     * <p>当会话总 token 数超过 {@link #summaryThreshold} 时：
     * <ol>
     *   <li>将较早的消息（保留近期 {@link #keepRecentRounds} 轮）提取出来</li>
     *   <li>调用 LLM 生成摘要</li>
     *   <li>用摘要 SYSTEM 消息 + 近期消息替换原始历史</li>
     * </ol>
     */
    private void tryCompress(String sessionId) {
        try {
            int totalTokens = delegate.getTokenCount(sessionId);
            if (totalTokens <= summaryThreshold) {
                return;
            }

            List<ChatMessage> history = delegate.getHistory(sessionId);
            if (history.size() <= keepRecentRounds * 2) {
                return; // 消息太少，不值得压缩
            }

            // 分割：较早的消息（待压缩） + 近期消息（保留）
            int splitIndex = history.size() - keepRecentRounds * 2;
            List<ChatMessage> toCompress = new ArrayList<>(history.subList(0, splitIndex));
            List<ChatMessage> toKeep = new ArrayList<>(history.subList(splitIndex, history.size()));

            // 检查是否已有摘要（第一条如果是 SYSTEM 摘要则合并）
            String existingSummary = null;
            if (!toCompress.isEmpty()
                    && toCompress.get(0).getRole() == ChatMessage.Role.SYSTEM
                    && toCompress.get(0).getContent() != null
                    && toCompress.get(0).getContent().startsWith("[对话摘要]")) {
                existingSummary = toCompress.get(0).getContent();
                toCompress = toCompress.subList(1, toCompress.size());
            }

            // 构建待压缩文本
            StringBuilder sb = new StringBuilder();
            if (existingSummary != null) {
                sb.append(existingSummary).append("\n\n");
            }
            for (ChatMessage msg : toCompress) {
                if (msg == null || msg.getContent() == null) continue;
                sb.append(msg.getRole()).append(": ").append(msg.getContent()).append('\n');
            }

            // 调用 LLM 生成摘要
            String summary = generateSummary(sb.toString());
            if (summary == null || summary.isBlank()) {
                log.warn("[SummaryChatMemory] 摘要生成失败, 跳过压缩");
                return;
            }

            // 构建压缩后的历史：摘要 + 近期消息
            List<ChatMessage> compressed = new ArrayList<>();
            compressed.add(ChatMessage.system("[对话摘要] " + summary));
            compressed.addAll(toKeep);

            // 替换底层历史
            delegate.clear(sessionId);
            delegate.addMessages(sessionId, compressed);

            log.info("[SummaryChatMemory] 会话 {} 压缩完成, 原始 {} 条消息 → {} 条, token {} → {}",
                    sessionId, history.size(), compressed.size(),
                    totalTokens, delegate.getTokenCount(sessionId));
        } catch (Exception e) {
            log.warn("[SummaryChatMemory] 压缩失败, sessionId={}: {}", sessionId, e.getMessage());
        }
    }

    /**
     * 调用 LLM 生成对话摘要。
     */
    private String generateSummary(String conversationText) {
        if (llmProviderRouter == null) {
            return null;
        }
        try {
            LlmProvider llm = llmProviderRouter.active();
            String prompt = String.format(SUMMARY_PROMPT, conversationText);
            return llm.chat("你是一个对话摘要助手，善于提取关键信息。", prompt, null);
        } catch (Exception e) {
            log.warn("[SummaryChatMemory] LLM 摘要调用失败: {}", e.getMessage());
            return null;
        }
    }
}
