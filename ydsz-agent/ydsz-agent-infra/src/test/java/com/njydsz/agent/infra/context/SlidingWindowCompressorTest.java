package com.njydsz.agent.infra.context;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.njydsz.agent.domain.model.ChatMessage;
import com.njydsz.agent.domain.model.MessageRole;

/**
 * {@link SlidingWindowCompressor} 单元测试。
 *
 * <p>验证按 Token 预算压缩时，System 消息始终保留、最近消息优先保留、旧消息按预算丢弃。
 *
 * @author ydsz-team
 * @since 26.09.14
 */
class SlidingWindowCompressorTest {

  /** 字符/Token 估算比例（与默认一致） */
  private static final double TOKEN_CHAR_RATIO = 2.5;

  /** 被测压缩器 */
  private final SlidingWindowCompressor compressor = new SlidingWindowCompressor();

  /**
   * 空列表与 null 应返回空列表或原引用。
   */
  @Test
  @DisplayName("空输入直接返回")
  void emptyInputShouldReturnEmpty() {
    assertTrue(compressor.compress(null, 100).isEmpty());
    assertTrue(compressor.compress(List.of(), 100).isEmpty());
  }

  /**
   * 预算充足时，应保留全部消息。
   */
  @Test
  @DisplayName("预算充足保留全部消息")
  void sufficientBudgetShouldKeepAllMessages() {
    List<ChatMessage> messages =
        List.of(
            ChatMessage.system("system prompt"),
            ChatMessage.user("short 1", "conv"),
            ChatMessage.assistant("short 2", "conv", null));

    List<ChatMessage> result = compressor.compress(messages, 1000);

    assertEquals(messages, result);
  }

  /**
   * System 消息始终保留，且至少保留 1 条最近非 System 消息。
   */
  @Test
  @DisplayName("System 消息保留且至少保留 1 条最近非 System 消息")
  void systemMessagesShouldAlwaysBeKeptWithLatestNonSystem() {
    List<ChatMessage> messages =
        List.of(
            ChatMessage.system("system prompt"),
            ChatMessage.user("u", "conv"),
            ChatMessage.assistant("a", "conv", null));

    List<ChatMessage> result = compressor.compress(messages, 1);

    assertEquals(2, result.size());
    assertEquals(MessageRole.SYSTEM, result.get(0).getRole());
    assertEquals("a", result.get(1).getContent());
  }

  /**
   * 预算不足时，应至少保留 1 条最近非 System 消息。
   */
  @Test
  @DisplayName("预算不足时至少保留 1 条最近非 System 消息")
  void tightBudgetShouldKeepLatestNonSystem() {
    List<ChatMessage> messages =
        List.of(
            ChatMessage.system("system prompt"),
            ChatMessage.user("first question with many chars", "conv"),
            ChatMessage.assistant("last answer", "conv", null));

    List<ChatMessage> result = compressor.compress(messages, 5);

    assertTrue(result.size() >= 2);
    assertEquals(MessageRole.SYSTEM, result.get(0).getRole());
    assertEquals("last answer", result.get(result.size() - 1).getContent());
  }

  /**
   * 预算有限时，丢弃超出预算的最旧非 System 消息，保留最新消息。
   */
  @Test
  @DisplayName("按预算丢弃最旧非 System 消息")
  void shouldDropOldestNonSystemWhenBudgetExceeded() {
    ChatMessage sys = ChatMessage.system("system prompt");
    ChatMessage oldMsg = ChatMessage.user("old message content is long", "conv");
    ChatMessage recentMsg = ChatMessage.assistant("recent message", "conv", null);
    List<ChatMessage> messages = List.of(sys, oldMsg, recentMsg);

    int recentTokens = estimateTokens(recentMsg);
    int budget = recentTokens + 2;
    List<ChatMessage> result = compressor.compress(messages, budget);

    assertTrue(result.contains(sys), "System 消息应保留");
    assertTrue(result.contains(recentMsg), "最近非 System 消息应保留");
    assertTrue(!result.contains(oldMsg), "最旧非 System 消息应被丢弃");
    assertTrue(result.indexOf(sys) < result.indexOf(recentMsg), "System 消息应在最前");
  }

  /**
   * 预算极小时仍保留 System 消息与 1 条最近非 System 消息。
   */
  @Test
  @DisplayName("预算极小时仍保留 System 与 1 条最近非 System 消息")
  void minimalBudgetShouldKeepSystemAndLatestNonSystem() {
    String longSystem = "a".repeat(100);
    ChatMessage sys = ChatMessage.system(longSystem);
    List<ChatMessage> messages = List.of(sys, ChatMessage.user("u", "conv"));

    List<ChatMessage> result = compressor.compress(messages, 1);

    assertEquals(2, result.size());
    assertEquals(MessageRole.SYSTEM, result.get(0).getRole());
    assertEquals("u", result.get(1).getContent());
  }

  private static int estimateTokens(ChatMessage message) {
    return (int) Math.ceil(message.getContent().length() / TOKEN_CHAR_RATIO);
  }
}
