package com.njydsz.common.queue.queue;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.concurrent.Executors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StreamOperations;

import com.njydsz.common.queue.config.QueueProperties;
import com.njydsz.common.queue.service.IMessagePublisher;
import com.njydsz.common.queue.service.IMessageSubscriber;
import com.njydsz.common.queue.service.impl.RedisStreamPublisher;
import com.njydsz.common.queue.service.impl.RedisStreamSubscriber;
import com.njydsz.common.redis.service.RedisService;

/**
 * {@link RedisStreamMQ} 工厂测试。
 *
 * <p>验证基于 RedisService 复用连接的工厂行为：
 * <ul>
 *   <li>构造器参数校验</li>
 *   <li>createPublisher / createSubscriber 创建正确实现类型</li>
 *   <li>关闭语义（幂等、状态变更）</li>
 *   <li>关闭后操作被拒绝</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@DisplayName("RedisStreamMQ 工厂测试")
class RedisStreamMQTest {

    private RedisService redisService;
    private QueueProperties properties;
    private java.util.concurrent.ExecutorService executor;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        redisService = mock(RedisService.class);
        RedisTemplate<String, Object> redisTemplate = mock(RedisTemplate.class);
        StreamOperations<String, Object, Object> streamOps = mock(StreamOperations.class);
        when(redisService.getRedisTemplate()).thenReturn(redisTemplate);
        when(redisTemplate.opsForStream()).thenReturn(streamOps);

        properties = new QueueProperties();
        executor = Executors.newSingleThreadExecutor();
    }

    @AfterEach
    void tearDown() {
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("构造器拒绝 null QueueProperties")
    void testConstructorNullProperties() {
        assertThrows(Exception.class,
                () -> new RedisStreamMQ(redisService, null, executor));
    }

    @Test
    @DisplayName("构造器从 RedisService 获取 RedisTemplate（连接复用）")
    void testConstructorReusesRedisServiceConnection() {
        new RedisStreamMQ(redisService, properties, executor);

        verify(redisService).getRedisTemplate();
    }

    @Test
    @DisplayName("createPublisher 返回 RedisStreamPublisher 实例")
    void testCreatePublisherReturnsRedisStreamPublisher() {
        RedisStreamMQ mq = new RedisStreamMQ(redisService, properties, executor);

        IMessagePublisher publisher = mq.createPublisher("test-channel");

        assertNotNull(publisher);
        assertInstanceOf(RedisStreamPublisher.class, publisher);
        assertEquals("test-channel", publisher.getChannel());
    }

    @Test
    @DisplayName("createSubscriber 返回 RedisStreamSubscriber 实例")
    void testCreateSubscriberReturnsRedisStreamSubscriber() {
        // 注意：RedisStreamSubscriber 构造时调用 ensureGroup()，会触发 Redis 操作
        // 由于 RedisTemplate 是 mock，createGroup 会返回 null（不抛异常）
        RedisStreamMQ mq = new RedisStreamMQ(redisService, properties, executor);

        IMessageSubscriber subscriber = mq.createSubscriber("test-channel");

        assertNotNull(subscriber);
        assertInstanceOf(RedisStreamSubscriber.class, subscriber);
        assertNotNull(subscriber.getConsumerId());
    }

    @Test
    @DisplayName("createPublisher 拒绝 null/空通道")
    void testCreatePublisherRejectsEmptyChannel() {
        RedisStreamMQ mq = new RedisStreamMQ(redisService, properties, executor);

        assertThrows(Exception.class, () -> mq.createPublisher(null));
        assertThrows(Exception.class, () -> mq.createPublisher(""));
    }

    @Test
    @DisplayName("createSubscriber 拒绝 null/空通道")
    void testCreateSubscriberRejectsEmptyChannel() {
        RedisStreamMQ mq = new RedisStreamMQ(redisService, properties, executor);

        assertThrows(Exception.class, () -> mq.createSubscriber(null));
        assertThrows(Exception.class, () -> mq.createSubscriber(""));
    }

    @Test
    @DisplayName("getType 返回 'Redis-Stream'")
    void testGetType() {
        RedisStreamMQ mq = new RedisStreamMQ(redisService, properties, executor);

        assertEquals("Redis-Stream", mq.getType());
    }

    @Test
    @DisplayName("isClosed 初始为 false")
    void testIsClosedInitiallyFalse() {
        RedisStreamMQ mq = new RedisStreamMQ(redisService, properties, executor);

        assertFalse(mq.isClosed());
    }

    @Test
    @DisplayName("close 后 isClosed 返回 true")
    void testCloseSetsIsClosedTrue() {
        RedisStreamMQ mq = new RedisStreamMQ(redisService, properties, executor);

        mq.close();

        assertTrue(mq.isClosed());
    }

    @Test
    @DisplayName("close 是幂等操作（多次调用不抛异常）")
    void testCloseIsIdempotent() {
        RedisStreamMQ mq = new RedisStreamMQ(redisService, properties, executor);

        mq.close();
        mq.close();
        mq.close();

        assertTrue(mq.isClosed());
    }

    @Test
    @DisplayName("close 后 createPublisher 被拒绝")
    void testCreatePublisherAfterCloseRejected() {
        RedisStreamMQ mq = new RedisStreamMQ(redisService, properties, executor);
        mq.close();

        assertThrows(Exception.class, () -> mq.createPublisher("channel"));
    }

    @Test
    @DisplayName("close 后 createSubscriber 被拒绝")
    void testCreateSubscriberAfterCloseRejected() {
        RedisStreamMQ mq = new RedisStreamMQ(redisService, properties, executor);
        mq.close();

        assertThrows(Exception.class, () -> mq.createSubscriber("channel"));
    }
}
