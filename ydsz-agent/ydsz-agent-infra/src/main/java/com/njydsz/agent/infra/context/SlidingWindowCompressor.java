package com.njydsz.agent.infra.context;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.njydsz.agent.domain.context.ContextCompressor;
import com.njydsz.agent.domain.model.ChatMessage;
import com.njydsz.agent.domain.model.MessageRole;

/**
 * 滑动窗口压缩策略 — 丢弃最旧的消息，保留最近 N 轮和所有 System 消息。
 *
 * <p>最简单的压缩策略：保留 System Prompt + 最近 maxSize 条非 System 消息。
 * 适用于大多数对话场景，无 LLM 调用开销。
 *
 * @author ydsz-team
 * @since 26.09.13
 */
@Component
public class SlidingWindowCompressor implements ContextCompressor {

  @Override
  public List<ChatMessage> compress(List<ChatMessage> messages, int maxSize) {
    if (messages.size() <= maxSize) {
      return messages;
    }
    // 分离 System 消息和非 System 消息
    List<ChatMessage> systemMessages = new ArrayList<>(4);
    List<ChatMessage> nonSystemMessages = new ArrayList<>(messages.size());
    for (ChatMessage msg : messages) {
      if (msg.getRole() == MessageRole.SYSTEM) {
        systemMessages.add(msg);
      } else {
        nonSystemMessages.add(msg);
      }
    }
    // 保留 System 消息 + 最近 maxSize - systemCount 条非 System 消息
    int keepNonSystem = Math.max(maxSize - systemMessages.size(), 1);
    int skipCount = Math.max(nonSystemMessages.size() - keepNonSystem, 0);
    List<ChatMessage> result = new ArrayList<>(maxSize);
    result.addAll(systemMessages);
    result.addAll(nonSystemMessages.subList(skipCount, nonSystemMessages.size()));
    return result;
  }

  @Override
  public String getName() {
    return "sliding-window";
  }
}
