package com.njydsz.pmis.agent.engine.memory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ContextWindow 上下文窗口管理测试（P1-3 落地）
 *
 * <p>覆盖：
 * <ul>
 *   <li>truncate(): null/空列表返回空列表</li>
 *   <li>truncate(): 保留所有 SYSTEM 消息</li>
 *   <li>truncate(): 保留最近的非 SYSTEM 消息</li>
 *   <li>truncate(): 超限时从最旧的非 SYSTEM 消息开始删除</li>
 *   <li>truncate(): minRounds 保证至少保留 N 轮</li>
 *   <li>totalTokens(): 正确累加 token 数</li>
 *   <li>fillTokenCounts(): 自动填充缺失的 tokenCount</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P1-3)
 */
@DisplayName("ContextWindow 上下文窗口管理测试")
class ContextWindowTest {

    // ==================== 边界值测试 ====================

    @Nested
    @DisplayName("边界值测试")
    class BoundaryTest {

        @Test
        @DisplayName("truncate(null) 返回空列表")
        void shouldReturnEmptyForNull() {
            List<ChatMessage> result = ContextWindow.truncate(null, 100);
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("truncate(空列表) 返回空列表")
        void shouldReturnEmptyForEmptyList() {
            List<ChatMessage> result = ContextWindow.truncate(new ArrayList<>(), 100);
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("maxTokens=0 时使用默认值")
        void shouldUseDefaultWhenMaxTokensZero() {
            List<ChatMessage> messages = List.of(
                    ChatMessage.user("hello"),
                    ChatMessage.assistant("world")
            );
            List<ChatMessage> result = ContextWindow.truncate(messages, 0);
            // 不应抛异常，且保留了消息
            assertThat(result).hasSizeGreaterThanOrEqualTo(1);
        }
    }

    // ==================== SYSTEM 消息保留测试 ====================

    @Nested
    @DisplayName("SYSTEM 消息保留测试")
    class SystemMessageTest {

        @Test
        @DisplayName("truncate 始终保留所有 SYSTEM 消息")
        void shouldAlwaysKeepSystemMessages() {
            List<ChatMessage> messages = new ArrayList<>();
            messages.add(ChatMessage.system("你是助手"));
            messages.add(ChatMessage.user("你好"));

            // 设置极小的 maxTokens，迫使截断
            List<ChatMessage> result = ContextWindow.truncate(messages, 1, 0);

            // SYSTEM 消息应被保留
            assertThat(result).anyMatch(m -> m.getRole() == ChatMessage.Role.SYSTEM);
        }

        @Test
        @DisplayName("多个 SYSTEM 消息都被保留")
        void shouldKeepAllSystemMessages() {
            List<ChatMessage> messages = new ArrayList<>();
            messages.add(ChatMessage.system("系统提示 1"));
            messages.add(ChatMessage.system("系统提示 2"));
            messages.add(ChatMessage.user("你好"));

            List<ChatMessage> result = ContextWindow.truncate(messages, 5, 0);

            long systemCount = result.stream()
                    .filter(m -> m.getRole() == ChatMessage.Role.SYSTEM)
                    .count();
            assertThat(systemCount).isEqualTo(2);
        }
    }

    // ==================== 截断策略测试 ====================

    @Nested
    @DisplayName("截断策略测试")
    class TruncationTest {

        @Test
        @DisplayName("超限时从最旧的非 SYSTEM 消息开始删除")
        void shouldRemoveOldestNonSystemMessagesWhenOverLimit() {
            List<ChatMessage> messages = new ArrayList<>();
            messages.add(ChatMessage.system("sys"));
            messages.add(ChatMessage.user("第一轮问题"));  // 最旧的，应被删除
            messages.add(ChatMessage.assistant("第一轮回答"));
            messages.add(ChatMessage.user("第二轮问题"));  // 最新的，应被保留
            messages.add(ChatMessage.assistant("第二轮回答"));

            // maxTokens = SYSTEM + 最近 2 条消息的 token（容纳最新一轮，丢弃最旧一轮）
            int maxTokens = TokenCounter.estimate("sys")
                    + TokenCounter.estimate("第二轮问题")
                    + TokenCounter.estimate("第二轮回答");
            List<ChatMessage> result = ContextWindow.truncate(messages, maxTokens, 0);

            // 最新的 2 条应被保留
            assertThat(result).anyMatch(m -> "第二轮问题".equals(m.getContent()));
            assertThat(result).anyMatch(m -> "第二轮回答".equals(m.getContent()));
            // 最旧的应被删除（在 minRounds=0 时）
            assertThat(result).noneMatch(m -> "第一轮问题".equals(m.getContent()));
        }

        @Test
        @DisplayName("未超限时保留所有消息")
        void shouldKeepAllWhenUnderLimit() {
            List<ChatMessage> messages = new ArrayList<>();
            messages.add(ChatMessage.system("sys"));
            messages.add(ChatMessage.user("hello"));
            messages.add(ChatMessage.assistant("world"));

            List<ChatMessage> result = ContextWindow.truncate(messages, 10000);

            assertThat(result).hasSize(3);
        }

        @Test
        @DisplayName("minRounds 保证至少保留 N 轮（即使超限）")
        void shouldKeepMinRoundsEvenWhenOverLimit() {
            List<ChatMessage> messages = new ArrayList<>();
            messages.add(ChatMessage.user("第一轮问题"));
            messages.add(ChatMessage.assistant("第一轮回答"));

            // 设置 maxTokens=1，但 minRounds=1，应至少保留 1 轮（2 条消息）
            List<ChatMessage> result = ContextWindow.truncate(messages, 1, 1);

            assertThat(result).hasSize(2);
        }
    }

    // ==================== totalTokens 测试 ====================

    @Nested
    @DisplayName("totalTokens 测试")
    class TotalTokensTest {

        @Test
        @DisplayName("totalTokens(null) 返回 0")
        void shouldReturnZeroForNull() {
            assertThat(ContextWindow.totalTokens(null)).isEqualTo(0);
        }

        @Test
        @DisplayName("totalTokens(空列表) 返回 0")
        void shouldReturnZeroForEmpty() {
            assertThat(ContextWindow.totalTokens(new ArrayList<>())).isEqualTo(0);
        }

        @Test
        @DisplayName("totalTokens 正确累加已设置的 tokenCount")
        void shouldSumPreSetTokenCounts() {
            ChatMessage m1 = new ChatMessage(ChatMessage.Role.USER, "hello");
            m1.setTokenCount(10);
            ChatMessage m2 = new ChatMessage(ChatMessage.Role.ASSISTANT, "world");
            m2.setTokenCount(20);

            assertThat(ContextWindow.totalTokens(List.of(m1, m2))).isEqualTo(30);
        }

        @Test
        @DisplayName("totalTokens 自动估算未设置的 tokenCount")
        void shouldEstimateMissingTokenCounts() {
            ChatMessage m1 = new ChatMessage(ChatMessage.Role.USER, "hello");
            // 不设置 tokenCount，应自动估算
            ChatMessage m2 = new ChatMessage(ChatMessage.Role.ASSISTANT, "world");
            m2.setTokenCount(20);

            int total = ContextWindow.totalTokens(List.of(m1, m2));
            assertThat(total).isGreaterThan(20); // 应包含 m1 的估算值
        }
    }

    // ==================== fillTokenCounts 测试 ====================

    @Nested
    @DisplayName("fillTokenCounts 测试")
    class FillTokenCountsTest {

        @Test
        @DisplayName("自动填充缺失的 tokenCount")
        void shouldFillMissingTokenCounts() {
            ChatMessage m1 = new ChatMessage(ChatMessage.Role.USER, "hello");
            ChatMessage m2 = new ChatMessage(ChatMessage.Role.ASSISTANT, "world");
            List<ChatMessage> messages = new ArrayList<>();
            messages.add(m1);
            messages.add(m2);

            ContextWindow.fillTokenCounts(messages);

            assertThat(m1.getTokenCount()).isGreaterThan(0);
            assertThat(m2.getTokenCount()).isGreaterThan(0);
        }

        @Test
        @DisplayName("已设置的 tokenCount 不被覆盖")
        void shouldNotOverrideExistingTokenCounts() {
            ChatMessage m1 = new ChatMessage(ChatMessage.Role.USER, "hello");
            m1.setTokenCount(999);

            ContextWindow.fillTokenCounts(List.of(m1));

            assertThat(m1.getTokenCount()).isEqualTo(999);
        }

        @Test
        @DisplayName("null 列表不抛异常")
        void shouldNotThrowForNullList() {
            ContextWindow.fillTokenCounts(null); // 不抛异常即可
        }
    }
}
