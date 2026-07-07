package com.njydsz.pmis.agent.engine.memory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ChatMemory 对话记忆管理器单元测试（P1-3 落地）
 *
 * <p>覆盖：
 * <ul>
 *   <li>addMessage: 单条/批量添加，自动计算 tokenCount</li>
 *   <li>getHistory: 获取只读副本</li>
 *   <li>getTokenCount: 累加 token 数</li>
 *   <li>getMessageCount: 消息数统计</li>
 *   <li>clear / clearAll: 清除会话</li>
 *   <li>sessionId 隔离：不同会话互不影响</li>
 *   <li>自动截断：超过 maxTokens 时自动截断</li>
 *   <li>边界值：null/空 sessionId 跳过</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P1-3)
 */
@DisplayName("ChatMemory 对话记忆管理器测试")
class ChatMemoryTest {

    private ChatMemory chatMemory;

    @BeforeEach
    void setUp() {
        // 使用较小的 maxTokens 方便测试截断
        chatMemory = new ChatMemory(100, 1);
    }

    // ==================== addMessage 测试 ====================

    @Nested
    @DisplayName("addMessage 添加消息测试")
    class AddMessageTest {

        @Test
        @DisplayName("添加单条消息后可获取到")
        void shouldAddAndGetMessage() {
            chatMemory.addMessage("s1", ChatMessage.user("你好"));

            List<ChatMessage> history = chatMemory.getHistory("s1");
            assertThat(history).hasSize(1);
            assertThat(history.get(0).getContent()).isEqualTo("你好");
            assertThat(history.get(0).getRole()).isEqualTo(ChatMessage.Role.USER);
        }

        @Test
        @DisplayName("添加消息时自动计算 tokenCount")
        void shouldAutoCalculateTokenCount() {
            chatMemory.addMessage("s1", ChatMessage.user("hello"));

            List<ChatMessage> history = chatMemory.getHistory("s1");
            assertThat(history.get(0).getTokenCount()).isGreaterThan(0);
        }

        @Test
        @DisplayName("已设置的 tokenCount 不被覆盖")
        void shouldKeepExistingTokenCount() {
            ChatMessage msg = new ChatMessage(ChatMessage.Role.USER, "hello");
            msg.setTokenCount(999);

            chatMemory.addMessage("s1", msg);

            List<ChatMessage> history = chatMemory.getHistory("s1");
            assertThat(history.get(0).getTokenCount()).isEqualTo(999);
        }

        @Test
        @DisplayName("批量添加消息")
        void shouldAddMultipleMessages() {
            chatMemory.addMessages("s1", List.of(
                    ChatMessage.system("sys"),
                    ChatMessage.user("你好"),
                    ChatMessage.assistant("你好，我是助手")
            ));

            assertThat(chatMemory.getMessageCount("s1")).isEqualTo(3);
        }

        @Test
        @DisplayName("null sessionId 跳过")
        void shouldSkipNullSessionId() {
            chatMemory.addMessage(null, ChatMessage.user("hello"));
            chatMemory.addMessage("", ChatMessage.user("hello"));

            assertThat(chatMemory.getActiveSessionCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("null 消息跳过")
        void shouldSkipNullMessage() {
            chatMemory.addMessage("s1", null);

            assertThat(chatMemory.getMessageCount("s1")).isEqualTo(0);
        }
    }

    // ==================== getHistory 测试 ====================

    @Nested
    @DisplayName("getHistory 获取历史测试")
    class GetHistoryTest {

        @Test
        @DisplayName("获取只读副本（修改不影响原列表）")
        void shouldReturnUnmodifiableList() {
            chatMemory.addMessage("s1", ChatMessage.user("hello"));

            List<ChatMessage> history = chatMemory.getHistory("s1");
            assertThatThrownByUnsupportedOperation(() -> history.add(ChatMessage.user("hack")));
        }

        @Test
        @DisplayName("不存在的 sessionId 返回空列表")
        void shouldReturnEmptyForUnknownSession() {
            List<ChatMessage> history = chatMemory.getHistory("unknown");

            assertThat(history).isEmpty();
        }

        @Test
        @DisplayName("null sessionId 返回空列表")
        void shouldReturnEmptyForNullSessionId() {
            assertThat(chatMemory.getHistory(null)).isEmpty();
        }
    }

    // ==================== sessionId 隔离测试 ====================

    @Nested
    @DisplayName("sessionId 隔离测试")
    class SessionIsolationTest {

        @Test
        @DisplayName("不同 sessionId 的消息互不影响")
        void shouldIsolateDifferentSessions() {
            chatMemory.addMessage("s1", ChatMessage.user("session1 消息"));
            chatMemory.addMessage("s2", ChatMessage.user("session2 消息"));

            assertThat(chatMemory.getMessageCount("s1")).isEqualTo(1);
            assertThat(chatMemory.getMessageCount("s2")).isEqualTo(1);
            assertThat(chatMemory.getHistory("s1").get(0).getContent()).isEqualTo("session1 消息");
            assertThat(chatMemory.getHistory("s2").get(0).getContent()).isEqualTo("session2 消息");
        }

        @Test
        @DisplayName("清除 s1 不影响 s2")
        void shouldNotAffectOtherSessionWhenClearOne() {
            chatMemory.addMessage("s1", ChatMessage.user("session1"));
            chatMemory.addMessage("s2", ChatMessage.user("session2"));

            chatMemory.clear("s1");

            assertThat(chatMemory.getMessageCount("s1")).isEqualTo(0);
            assertThat(chatMemory.getMessageCount("s2")).isEqualTo(1);
        }
    }

    // ==================== 自动截断测试 ====================

    @Nested
    @DisplayName("自动截断测试")
    class AutoTruncationTest {

        @Test
        @DisplayName("超过 maxTokens 时自动截断，保留 SYSTEM 消息")
        void shouldTruncateWhenOverLimit() {
            // maxTokens=100，minRounds=1，添加大量消息触发截断
            StringBuilder longContent = new StringBuilder();
            for (int i = 0; i < 50; i++) {
                longContent.append("这是一段很长的中文内容");
            }

            chatMemory.addMessage("s1", ChatMessage.system("system prompt"));
            for (int i = 0; i < 5; i++) {
                chatMemory.addMessage("s1", ChatMessage.user(longContent.toString()));
                chatMemory.addMessage("s1", ChatMessage.assistant(longContent.toString()));
            }

            // 应已截断，SYSTEM 消息必须保留
            List<ChatMessage> history = chatMemory.getHistory("s1");
            assertThat(history).anyMatch(m -> m.getRole() == ChatMessage.Role.SYSTEM);
            // 截断后消息数应远小于添加的总数（11 条）
            assertThat(history.size()).isLessThan(11);
            // 由于 minRounds=1，至少保留 1 轮非 SYSTEM 消息
            assertThat(history.stream()
                    .filter(m -> m.getRole() != ChatMessage.Role.SYSTEM)
                    .count()).isGreaterThanOrEqualTo(1);
        }

        @Test
        @DisplayName("未超限时不截断")
        void shouldNotTruncateWhenUnderLimit() {
            chatMemory.addMessage("s1", ChatMessage.user("hello"));
            chatMemory.addMessage("s1", ChatMessage.assistant("world"));

            assertThat(chatMemory.getMessageCount("s1")).isEqualTo(2);
        }
    }

    // ==================== clear / clearAll 测试 ====================

    @Nested
    @DisplayName("清除测试")
    class ClearTest {

        @Test
        @DisplayName("clear 删除指定会话")
        void shouldClearSpecificSession() {
            chatMemory.addMessage("s1", ChatMessage.user("hello"));
            assertThat(chatMemory.getMessageCount("s1")).isEqualTo(1);

            chatMemory.clear("s1");

            assertThat(chatMemory.getMessageCount("s1")).isEqualTo(0);
        }

        @Test
        @DisplayName("clearAll 删除所有会话")
        void shouldClearAllSessions() {
            chatMemory.addMessage("s1", ChatMessage.user("hello"));
            chatMemory.addMessage("s2", ChatMessage.user("world"));
            assertThat(chatMemory.getActiveSessionCount()).isEqualTo(2);

            chatMemory.clearAll();

            assertThat(chatMemory.getActiveSessionCount()).isEqualTo(0);
        }

        @Test
        @DisplayName("clear(null) 不抛异常")
        void shouldNotThrowWhenClearNull() {
            chatMemory.clear(null); // 不抛异常即可
        }
    }

    // ==================== getActiveSessionCount 测试 ====================

    @Nested
    @DisplayName("会话统计测试")
    class SessionCountTest {

        @Test
        @DisplayName("getActiveSessionCount 返回活跃会话数")
        void shouldReturnActiveSessionCount() {
            assertThat(chatMemory.getActiveSessionCount()).isEqualTo(0);

            chatMemory.addMessage("s1", ChatMessage.user("hello"));
            assertThat(chatMemory.getActiveSessionCount()).isEqualTo(1);

            chatMemory.addMessage("s2", ChatMessage.user("world"));
            assertThat(chatMemory.getActiveSessionCount()).isEqualTo(2);

            chatMemory.clear("s1");
            assertThat(chatMemory.getActiveSessionCount()).isEqualTo(1);
        }
    }

    // ==================== LRU + 容量上限测试（P1-8） ====================

    @Nested
    @DisplayName("LRU 与容量上限测试（P1-8）")
    class LruAndCapacityTest {

        @Test
        @DisplayName("超过 maxSessions 时活跃会话数不超过上限（LRU 淘汰）")
        void shouldEvictOldSessionsWhenExceedCapacity() {
            // 使用小容量方便测试：maxSessions=5
            ChatMemory small = new ChatMemory(100, 1, 5, 30);

            // 写入 8 个会话，超过容量 5
            for (int i = 0; i < 8; i++) {
                small.addMessage("sess-" + i, ChatMessage.user("msg-" + i));
            }

            // 活跃会话数不应超过上限
            assertThat(small.getActiveSessionCount()).isLessThanOrEqualTo(5);
        }

        @Test
        @DisplayName("超过容量时触发淘汰：至少淘汰一个旧会话，总数不超过上限")
        void shouldEvictAtLeastOneSessionWhenOverCapacity() {
            // Caffeine 使用 W-TinyLFU（非严格 LRU），此处验证可靠的容量上限与淘汰触发
            ChatMemory small = new ChatMemory(100, 1, 3, 30);

            // 写入 4 个会话，超过容量 3
            small.addMessage("s1", ChatMessage.user("m1"));
            small.addMessage("s2", ChatMessage.user("m2"));
            small.addMessage("s3", ChatMessage.user("m3"));
            small.addMessage("s4", ChatMessage.user("m4"));
            // 触发 cleanUp 使淘汰即时生效
            int active = small.getActiveSessionCount();

            // 活跃会话数不超过上限
            assertThat(active).isLessThanOrEqualTo(3);
            // 4 个会话中至少有一个被淘汰（getMessageCount 返回 0）
            int evictedCount = 0;
            for (int i = 1; i <= 4; i++) {
                if (small.getMessageCount("s" + i) == 0) {
                    evictedCount++;
                }
            }
            assertThat(evictedCount).isGreaterThanOrEqualTo(1);
        }

        @Test
        @DisplayName("高压力写入：会话数始终不超过 maxSessions 上限（防 OOM）")
        void shouldNeverExceedCapacityUnderHeavyChurn() {
            // Caffeine W-TinyLFU 的具体淘汰对象在单元测试中难以确定性断言，
            // 此处验证 P1-8 的核心保证：无论写入多少会话，活跃数始终受 maxSessions 上限约束
            ChatMemory mem = new ChatMemory(100, 1, 10, 30);

            // 写入远超容量的会话
            for (int i = 0; i < 100; i++) {
                mem.addMessage("sess-" + i, ChatMessage.user("msg-" + i));
            }

            // 最终活跃会话数不超过上限（getActiveSessionCount 内部会触发 cleanUp）
            assertThat(mem.getActiveSessionCount()).isLessThanOrEqualTo(10);
            // 幸存的会话仍可正常读取（只读副本）
            int surviving = 0;
            for (int i = 0; i < 100; i++) {
                if (mem.getMessageCount("sess-" + i) > 0) {
                    surviving++;
                    assertThat(mem.getHistory("sess-" + i)).isNotEmpty();
                }
            }
            assertThat(surviving).isLessThanOrEqualTo(10);
            assertThat(surviving).isGreaterThan(0);
        }

        @Test
        @DisplayName("clearAll 后所有会话被清除")
        void shouldClearAllSessionsInCaffeine() {
            ChatMemory small = new ChatMemory(100, 1, 10, 30);
            small.addMessage("s1", ChatMessage.user("m1"));
            small.addMessage("s2", ChatMessage.user("m2"));
            assertThat(small.getActiveSessionCount()).isEqualTo(2);

            small.clearAll();

            assertThat(small.getActiveSessionCount()).isEqualTo(0);
            assertThat(small.getMessageCount("s1")).isEqualTo(0);
        }

        @Test
        @DisplayName("默认构造器使用默认容量与过期时间，无异常")
        void shouldCreateWithDefaults() {
            ChatMemory mem = new ChatMemory();
            mem.addMessage("s1", ChatMessage.user("hello"));
            assertThat(mem.getMessageCount("s1")).isEqualTo(1);
            // 默认容量上限 1000，活跃会话数远小于此
            assertThat(mem.getActiveSessionCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("过期会话：getHistory 返回空（模拟过期后读取）")
        void shouldReturnEmptyForEvictedSession() {
            // 使用 1 个会话容量 + 写入 2 个会话淘汰第一个
            ChatMemory tiny = new ChatMemory(100, 1, 1, 30);
            tiny.addMessage("only", ChatMessage.user("first"));
            tiny.addMessage("next", ChatMessage.user("second"));
            // 触发 cleanUp 使淘汰即时生效
            tiny.getActiveSessionCount();

            // only 被淘汰，getHistory 返回空
            assertThat(tiny.getHistory("only")).isEmpty();
            assertThat(tiny.getMessageCount("only")).isEqualTo(0);
        }
    }

    // ==================== 辅助方法 ====================

    /** 验证列表是不可变的（修改应抛 UnsupportedOperationException） */
    private void assertThatThrownByUnsupportedOperation(Runnable action) {
        try {
            action.run();
            throw new AssertionError("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // 预期行为
        }
    }
}
