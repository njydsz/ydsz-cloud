package com.njydsz.pmis.literule.distributed;

import com.njydsz.pmis.literule.event.RuleConfigRefreshEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.springframework.context.ApplicationEventPublisher;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * RedisRuleConfigBroadcaster 单元测试
 *
 * <p>使用 Mockito mock RedissonClient 与 ApplicationEventPublisher，
 * 验证广播/订阅/防循环的核心逻辑。不依赖真实 Redis 实例。
 *
 * @author ydsz-pmis-team
 * @since 1.5.0
 */
@DisplayName("Redis 规则配置广播器测试")
class RedisRuleConfigBroadcasterTest {

    private RedissonClient redissonClient;
    private RTopic topic;
    private ApplicationEventPublisher eventPublisher;
    private RedisRuleConfigBroadcaster broadcaster;

    @BeforeEach
    void setUp() {
        redissonClient = mock(RedissonClient.class);
        topic = mock(RTopic.class);
        when(redissonClient.getTopic(anyString())).thenReturn(topic);
        eventPublisher = mock(ApplicationEventPublisher.class);
        broadcaster = new RedisRuleConfigBroadcaster(redissonClient, "node-A", eventPublisher);
    }

    @Test
    @DisplayName("广播事件 - 应发布消息到 Redis Topic")
    void shouldBroadcastToRedisTopic() {
        RuleConfigRefreshEvent event = RuleConfigRefreshEvent.of("R001",
                RuleConfigRefreshEvent.ChangeType.UPDATE, "admin");

        broadcaster.broadcast(event, "node-A");

        verify(topic, times(1)).publish(anyString());
    }

    @Test
    @DisplayName("广播 Null 事件应安全忽略")
    void shouldIgnoreNullEvent() {
        broadcaster.broadcast(null, "node-A");

        verify(topic, never()).publish(anyString());
    }

    @Test
    @DisplayName("广播 - Redis 异常应安全降级不抛出")
    void shouldSafelyHandleRedisException() {
        RuleConfigRefreshEvent event = RuleConfigRefreshEvent.of("R001",
                RuleConfigRefreshEvent.ChangeType.UPDATE, "admin");
        doThrow(new RuntimeException("Redis down")).when(topic).publish(anyString());

        assertDoesNotThrow(() -> broadcaster.broadcast(event, "node-A"));
    }

    @Test
    @DisplayName("订阅 - 应调用 addListener 注册监听器")
    void shouldSubscribeListener() {
        broadcaster.subscribe();

        verify(topic, times(1)).addListener(eq(String.class), any());
    }

    @Test
    @DisplayName("订阅 - 重复调用应只订阅一次")
    void shouldNotSubscribeTwice() {
        broadcaster.subscribe();
        broadcaster.subscribe();

        verify(topic, times(1)).addListener(eq(String.class), any());
    }

    @Test
    @DisplayName("isAvailable - Redis 正常时返回 true")
    void shouldReturnTrueWhenRedisAvailable() {
        when(topic.countListeners()).thenReturn(0);

        assertTrue(broadcaster.isAvailable());
    }

    @Test
    @DisplayName("isAvailable - Redis 异常时返回 false")
    void shouldReturnFalseWhenRedisFails() {
        when(topic.countListeners()).thenThrow(new RuntimeException("Redis down"));

        assertFalse(broadcaster.isAvailable());
    }
}
