package com.njydsz.agent.infra.memory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.agent.domain.conversation.ConversationMemory;
import com.njydsz.agent.domain.gateway.LlmClient;
import com.njydsz.agent.domain.model.ChatMessage;
import com.njydsz.agent.domain.model.ChatRequest;
import com.njydsz.agent.domain.model.ChatResponse;

/**
 * 摘要压缩对话记忆
 *
 * <p>包装 {@link ConversationMemory}，当消息数超过 {@link #summaryThreshold} 时， 自动将较早的消息通过 LLM 压缩为摘要，只保留最近
 * {@link #keepRecentCount} 条原始消息。
 *
 * <h3>对标竞品</h3>
 *
 * <ul>
 *   <li>LangChain ConversationSummaryBufferMemory
 *   <li>OpenAI Assistants Thread（自动截断 + 摘要）
 *   <li>Dify 对话记忆压缩
 * </ul>
 *
 * <h3>执行流程</h3>
 *
 * <pre>
 * save() → 检查消息数 → 超过阈值?
 *   YES → load 全部 → 取较早消息 → LLM 摘要 → 用摘要替换 → 清除旧消息 → 保存摘要+最近消息
 *   NO  → 直接委托给 delegate
 * </pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class SummaryConversationMemory implements ConversationMemory {

  private static final Logger LOG = LoggerFactory.getLogger(SummaryConversationMemory.class);

  /** 摘要前缀 */
  private static final String SUMMARY_PREFIX = "[对话摘要] ";

  /** 委托的记忆实现 */
  private final ConversationMemory delegate;

  /** LLM 客户端（用于生成摘要） */
  private final LlmClient llmClient;

  /** 模型名称 */
  private final String model;

  /** 触发摘要的消息阈值 */
  private final int summaryThreshold;

  /** 保留最近消息数 */
  private final int keepRecentCount;

  /** 对话摘要缓存（conversationId → summary） */
  private final ConcurrentMap<String, String> conversationSummaries = new ConcurrentHashMap<>();

  public SummaryConversationMemory(
      ConversationMemory delegate,
      LlmClient llmClient,
      String model,
      int summaryThreshold,
      int keepRecentCount) {
    this.delegate = delegate;
    this.llmClient = llmClient;
    this.model = model;
    this.summaryThreshold = summaryThreshold > 0 ? summaryThreshold : 20;
    this.keepRecentCount = keepRecentCount > 0 ? keepRecentCount : 10;
  }

  @Override
  public void save(String conversationId, ChatMessage message) {
    delegate.save(conversationId, message);

    long count = delegate.count(conversationId);
    if (count > summaryThreshold) {
      tryCompress(conversationId);
    }
  }

  @Override
  public List<ChatMessage> load(String conversationId, int maxMessages) {
    List<ChatMessage> messages = new ArrayList<>();

    String summary = conversationSummaries.get(conversationId);
    if (summary != null && !summary.isBlank()) {
      messages.add(ChatMessage.system(SUMMARY_PREFIX + summary));
    }

    messages.addAll(delegate.load(conversationId, maxMessages));
    return messages;
  }

  @Override
  public void clear(String conversationId) {
    delegate.clear(conversationId);
    conversationSummaries.remove(conversationId);
  }

  @Override
  public long count(String conversationId) {
    return delegate.count(conversationId);
  }

  /** 压缩对话历史：将较早的消息摘要后替换 */
  private void tryCompress(String conversationId) {
    try {
      List<ChatMessage> allMessages = delegate.load(conversationId, Integer.MAX_VALUE);
      if (allMessages.size() <= summaryThreshold) {
        return;
      }

      int toSummarize = allMessages.size() - keepRecentCount;
      List<ChatMessage> oldMessages = allMessages.subList(0, toSummarize);
      List<ChatMessage> recentMessages = allMessages.subList(toSummarize, allMessages.size());

      String existingSummary = conversationSummaries.getOrDefault(conversationId, "");
      String summary = summarizeMessages(conversationId, oldMessages, existingSummary);

      if (summary != null && !summary.isBlank()) {
        conversationSummaries.put(conversationId, summary);
        delegate.clear(conversationId);
        for (ChatMessage msg : recentMessages) {
          delegate.save(conversationId, msg);
        }
        LOG.info(
            "[Memory-Summary] 对话压缩完成: convId={}, compressed={}, kept={}",
            conversationId,
            oldMessages.size(),
            recentMessages.size());
      }
    } catch (Exception e) {
      LOG.warn("[Memory-Summary] 对话压缩失败: convId={}, error={}", conversationId, e.getMessage());
    }
  }

  /** 调用 LLM 生成摘要 */
  private String summarizeMessages(
      String conversationId, List<ChatMessage> messages, String existingSummary) {
    StringBuilder sb = new StringBuilder();
    sb.append("请将以下对话历史压缩为简洁的摘要（不超过 500 字），保留关键信息、决策和上下文。\n");
    if (existingSummary != null && !existingSummary.isBlank()) {
      sb.append("\n已有摘要：\n").append(existingSummary).append("\n");
    }
    sb.append("\n需要追加压缩的对话：\n");
    for (ChatMessage msg : messages) {
      sb.append(msg.getRole()).append(": ").append(truncate(msg.getContent(), 500)).append("\n");
    }

    ChatRequest request =
        ChatRequest.builder()
            .model(model)
            .messages(
                List.of(
                    ChatMessage.system("你是对话摘要助手，负责将对话历史压缩为简洁摘要。"),
                    ChatMessage.user(sb.toString(), conversationId)))
            .temperature(0.3)
            .maxTokens(800)
            .build();

    ChatResponse response = llmClient.chat(request);
    return response.getContent();
  }

  private String truncate(String text, int maxLen) {
    if (text == null) return "";
    return text.length() > maxLen ? text.substring(0, maxLen) + "..." : text;
  }
}
