package com.njydsz.agent.domain.conversation;

import java.util.List;

import com.njydsz.agent.domain.model.ChatMessage;

/**
 * 对话记忆接口
 *
 * <p>抽象对话历史的存储与检索。实现可选择 Redis（短期）、PostgreSQL（持久化）、向量存储（语义检索）。
 *
 * <h3>策略</h3>
 *
 * <ul>
 *   <li><b>滑动窗口</b>：仅保留最近 N 轮对话
 *   <li><b>摘要压缩</b>：历史对话由 LLM 摘要后替换原始消息
 *   <li><b>语义检索</b>：从全部历史中检索语义相关的消息
 * </ul>
 *
 * <p><b>线程安全</b>：记忆存储多为跨请求共享的远程/缓存实现，实现须保证并发 save/load/clear 的线程安全， 且 load
 * 返回的消息列表不应被调用方原地修改（建议返回防御性拷贝）。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public interface ConversationMemory {

  /** 工具调用额外 Token 估算值（每个工具调用约 50 Token） */
  int TOOL_CALL_TOKEN_ESTIMATE = 50;

  /**
   * Token 感知加载时单次获取的最大消息数上限。
   *
   * <p>防止 {@link #loadWithTokenBudget} 调用 {@code load(convId, Integer.MAX_VALUE)} 导致全量加载，
   * 单次最多获取此数量的消息做 Token 预算截断。绝大多数会话的历史消息远小于此值。
   */
  int TOKEN_BUDGET_FETCH_LIMIT = 200;

  /**
   * 保存一条消息到对话历史
   *
   * @param conversationId 对话 ID
   * @param message 消息
   */
  void save(String conversationId, ChatMessage message);

  /**
   * 加载对话历史消息
   *
   * @param conversationId 对话 ID
   * @param maxMessages 最大返回消息数（滑动窗口）
   * @return 历史消息列表（按时间正序）
   */
  List<ChatMessage> load(String conversationId, int maxMessages);

  /**
   * 按 Token 预算加载对话历史（Token 感知截断）。
   *
   * <p>从最新消息向前累加，直到估算 Token 数超过预算为止。保证返回的消息总 Token 不超过 {@code tokenBudget}，
   * 同时至少返回 1 条消息（即使单条已超预算）。
   *
   * <p>默认实现最多获取 {@link #TOKEN_BUDGET_FETCH_LIMIT} 条最新消息做 Token 预算截断，
   * 避免加载全量历史导致 OOM 或超长延迟。对于绝大多数会话（消息数 < 200），返回结果与全量加载完全一致。
   *
   * @param conversationId 对话 ID
   * @param tokenBudget Token 预算（估算值）
   * @param tokenCharRatio Token 估算的字符系数（Char/Token）
   * @return 历史消息列表（按时间正序），总 Token 不超过预算
   */
  default List<ChatMessage> loadWithTokenBudget(
      String conversationId, int tokenBudget, double tokenCharRatio) {
    // P1-修复：使用有界加载替代 load(convId, Integer.MAX_VALUE)，防止全量加载导致 OOM
    List<ChatMessage> recentMessages = load(conversationId, TOKEN_BUDGET_FETCH_LIMIT);
    if (recentMessages.isEmpty()) {
      return recentMessages;
    }
    long currentTokens = 0;
    int includeUpTo = recentMessages.size() - 1;
    for (int i = recentMessages.size() - 1; i >= 0; i--) {
      ChatMessage msg = recentMessages.get(i);
      int msgTokens = estimateTokens(msg, tokenCharRatio);
      if (currentTokens + msgTokens > tokenBudget) {
        break;
      }
      currentTokens += msgTokens;
      includeUpTo = i;
    }
    return recentMessages.subList(includeUpTo, recentMessages.size());
  }

  /**
   * 估算单条消息的 Token 数（基于字符数）。
   *
   * @param message 消息
   * @param tokenCharRatio Token 估算的字符系数（Char/Token）
   * @return 估算 Token 数
   */
  static int estimateTokens(ChatMessage message, double tokenCharRatio) {
    if (message == null) {
      return 0;
    }
    int charCount = 0;
    if (message.getContent() != null) {
      charCount += message.getContent().length();
    }
    if (message.getMultimodalContent() != null) {
      charCount += message.getMultimodalContent().estimateTokenChars();
    }
    // 工具调用额外估算：每个工具调用约 50 Token
    if (message.getToolCalls() != null) {
      charCount += message.getToolCalls().size() * TOOL_CALL_TOKEN_ESTIMATE;
    }
    return Math.max(1, (int) Math.ceil(charCount / tokenCharRatio));
  }

  /**
   * 清除对话历史
   *
   * @param conversationId 对话 ID
   */
  void clear(String conversationId);

  /**
   * 获取对话消息数
   *
   * @param conversationId 对话 ID
   * @return 消息数
   */
  long count(String conversationId);
}
