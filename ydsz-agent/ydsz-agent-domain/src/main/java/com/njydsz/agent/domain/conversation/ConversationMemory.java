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
 * @since 1.0.0
 */
public interface ConversationMemory {

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
