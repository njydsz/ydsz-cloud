package com.njydsz.agent.infra.context;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.njydsz.agent.domain.context.ContextCompressor;
import com.njydsz.agent.domain.conversation.ConversationMemory;
import com.njydsz.agent.domain.model.ChatMessage;
import com.njydsz.agent.domain.model.MessageRole;

/**
 * 滑动窗口压缩策略 — 按 Token 预算丢弃最旧消息，保留最近若干轮与全部 System 消息。
 *
 * <p>无 LLM 调用开销，适用于大多数对话场景。压缩口径与 {@link ContextCompressor} 契约一致：
 * 第二个参数是 <b>Token 预算（估算值）</b>，本实现从最新消息向前累加直至预算耗尽，
 * 因此无需调用方预先把预算换算成条数。
 *
 * <p><b>保底规则</b>：无论预算多小，System 消息全量保留，且至少保留最近 1 条非 System 消息，
 * 避免压缩后出现「只有 System Prompt、没有对话内容」的空上下文。
 *
 * @author ydsz-team
 * @since 26.09.13
 */
@Component
public class SlidingWindowCompressor implements ContextCompressor {

  /** System 消息初始容量，避免扩容开销 */
  private static final int SYSTEM_MSG_CAPACITY = 4;

  @Override
  public List<ChatMessage> compress(List<ChatMessage> messages, int tokenBudget) {
    if (messages == null || messages.isEmpty()) {
      return messages != null ? messages : List.of();
    }
    // 分离 System 消息（始终保留）与非 System 消息
    List<ChatMessage> systemMessages = new ArrayList<>(SYSTEM_MSG_CAPACITY);
    List<ChatMessage> nonSystemMessages = new ArrayList<>(messages.size());
    for (ChatMessage msg : messages) {
      if (msg.getRole() == MessageRole.SYSTEM) {
        systemMessages.add(msg);
      } else {
        nonSystemMessages.add(msg);
      }
    }
    // System 消息优先占用预算，剩余预算供近期对话使用
    int remaining = Math.max(tokenBudget - estimateTotalTokens(systemMessages), 0);
    int start = resolveWindowStart(nonSystemMessages, remaining);
    if (start == 0) {
      return messages;
    }
    List<ChatMessage> result = new ArrayList<>(systemMessages.size() + nonSystemMessages.size());
    result.addAll(systemMessages);
    result.addAll(nonSystemMessages.subList(start, nonSystemMessages.size()));
    return result;
  }

  @Override
  public String getName() {
    return "sliding-window";
  }

  /**
   * 计算窗口起始下标（从最新消息向前累加直至预算耗尽）。
   *
   * @param nonSystemMessages 非 System 消息（时间正序）
   * @param remainingTokens 可供使用 Token 预算
   * @return 起始下标；0 表示全部保留
   */
  private int resolveWindowStart(List<ChatMessage> nonSystemMessages, int remainingTokens) {
    if (nonSystemMessages.isEmpty()) {
      return 0;
    }
    int accumulated = 0;
    int start = nonSystemMessages.size();
    for (int i = nonSystemMessages.size() - 1; i >= 0; i--) {
      int messageTokens = ConversationMemory.estimateTokens(
          nonSystemMessages.get(i), DEFAULT_TOKEN_CHAR_RATIO);
      if (accumulated + messageTokens > remainingTokens) {
        break;
      }
      accumulated += messageTokens;
      start = i;
    }
    // 保底：至少保留最近 1 条非 System 消息，避免压缩后无对话内容
    return start == nonSystemMessages.size() ? nonSystemMessages.size() - 1 : start;
  }

  /**
   * 估算消息列表的 Token 数（与 {@link ConversationMemory#estimateTokens} 同口径）。
   *
   * @param messages 消息列表
   * @return 估算 Token 总数
   */
  private int estimateTotalTokens(List<ChatMessage> messages) {
    int total = 0;
    for (ChatMessage message : messages) {
      total += ConversationMemory.estimateTokens(message, DEFAULT_TOKEN_CHAR_RATIO);
    }
    return total;
  }
}
