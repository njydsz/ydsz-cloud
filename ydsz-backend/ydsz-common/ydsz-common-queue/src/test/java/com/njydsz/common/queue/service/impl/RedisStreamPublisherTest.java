package com.njydsz.common.queue.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StreamOperations;

import com.njydsz.common.queue.domain.QueueMessage;

/**
 * {@link RedisStreamPublisher} 单元测试。
 *
 * <p>通过 Mockito 模拟 {@link RedisTemplate}，验证发布者将
 * {@link QueueMessage} 正确转换为 Stream Record 写入 Redis。
 * 覆盖：null 处理、字段填充、批量发布、顺序消息字段、构造校验。
 *
 * <p><b>关于未检警告的说明：</b>Mockito 的 {@code mock(Class)} 与
 * {@code ArgumentCaptor.forClass(Class)} 在泛型类型上受 Java 类型擦除限制，
 * 无法在不引入 {@code @SuppressWarnings} 的情况下完全消除未检警告。
 * 本测试类按规范要求不使用 {@code @SuppressWarnings} 注解，
 * 残留的未检警告由编译器输出，便于后续随 Mockito 升级（如 {@code ArgumentCaptor.captor()}
 * 类型推断 API）一并清理。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@DisplayName("RedisStreamPublisher 发布者测试")
class RedisStreamPublisherTest {

    private RedisTemplate<String, Object> redisTemplate;
    private StreamOperations<String, Object, Object> streamOps;
    private RedisStreamPublisher publisher;

    @BeforeEach
    void setUp() {
        redisTemplate = mock(RedisTemplate.class);
        streamOps = mock(StreamOperations.class);
        when(redisTemplate.opsForStream()).thenReturn(streamOps);

        publisher = new RedisStreamPublisher(redisTemplate, "test-stream");
    }

    @Test
    @DisplayName("构造器拒绝 null RedisTemplate")
    void testConstructorNullRedisTemplate() {
        assertThrows(IllegalArgumentException.class,
                () -> new RedisStreamPublisher(null, "channel"));
    }

    @Test
    @DisplayName("构造器拒绝空通道名称")
    void testConstructorEmptyChannel() {
        assertThrows(IllegalArgumentException.class,
                () -> new RedisStreamPublisher(redisTemplate, null));
        assertThrows(IllegalArgumentException.class,
                () -> new RedisStreamPublisher(redisTemplate, ""));
    }

    @Test
    @DisplayName("publish(null) 不调用 Redis")
    void testPublishNullDoesNothing() {
        publisher.publish((QueueMessage) null);

        verifyNoInteractions(streamOps);
    }

    @Test
    @DisplayName("publish(String) 将字符串包装为 QueueMessage 后发布")
    void testPublishStringWrapsAsQueueMessage() {
        publisher.publish("hello world");

        ArgumentCaptor<ObjectRecord<String, Map<String, String>>> captor =
                ArgumentCaptor.forClass(ObjectRecord.class);
        verify(streamOps).add(captor.capture());

        ObjectRecord<String, Map<String, String>> record = captor.getValue();
        assertEquals("test-stream", record.getStream());
        Map<String, String> fields = record.getValue();
        assertNotNull(fields.get("payload"));
        assertNotNull(fields.get("traceId"));
        assertEquals("0", fields.get("retryCount"));
    }

    @Test
    @DisplayName("publish(QueueMessage) 正确填充 payload/traceId/retryCount 字段")
    void testPublishQueueMessageFillsFields() {
        QueueMessage message = QueueMessage.of("payload-data");
        message.setRetryCount(3);

        publisher.publish(message);

        ArgumentCaptor<ObjectRecord<String, Map<String, String>>> captor =
                ArgumentCaptor.forClass(ObjectRecord.class);
        verify(streamOps).add(captor.capture());

        Map<String, String> fields = captor.getValue().getValue();
        assertNotNull(fields.get("payload"));
        assertEquals(message.getTraceId(), fields.get("traceId"));
        assertEquals("3", fields.get("retryCount"));
    }

    @Test
    @DisplayName("publish 顺序消息时填充 groupKey 和 sequence 字段")
    void testPublishSequentialMessageFillsSequentialFields() {
        QueueMessage message = QueueMessage.of("order-data");
        message.setSequential("order-group-1", 42L);

        publisher.publish(message);

        ArgumentCaptor<ObjectRecord<String, Map<String, String>>> captor =
                ArgumentCaptor.forClass(ObjectRecord.class);
        verify(streamOps).add(captor.capture());

        Map<String, String> fields = captor.getValue().getValue();
        assertEquals("order-group-1", fields.get("groupKey"));
        assertEquals("42", fields.get("sequence"));
    }

    @Test
    @DisplayName("publish 非顺序消息不包含 groupKey/sequence 字段")
    void testPublishNonSequentialMessageOmitsSequentialFields() {
        QueueMessage message = QueueMessage.of("plain");

        publisher.publish(message);

        ArgumentCaptor<ObjectRecord<String, Map<String, String>>> captor =
                ArgumentCaptor.forClass(ObjectRecord.class);
        verify(streamOps).add(captor.capture());

        Map<String, String> fields = captor.getValue().getValue();
        assertNull(fields.get("groupKey"));
        assertNull(fields.get("sequence"));
    }

    @Test
    @DisplayName("publish(QueueMessage) retryCount 为 null 时默认填 '0'")
    void testPublishNullRetryCountDefaultsToZero() {
        QueueMessage message = new QueueMessage();
        message.setBody("body");
        message.setRetryCount(null);

        publisher.publish(message);

        ArgumentCaptor<ObjectRecord<String, Map<String, String>>> captor =
                ArgumentCaptor.forClass(ObjectRecord.class);
        verify(streamOps).add(captor.capture());

        Map<String, String> fields = captor.getValue().getValue();
        assertEquals("0", fields.get("retryCount"));
    }

    @Test
    @DisplayName("publishBatch(null/empty) 不调用 Redis")
    void testPublishBatchNullOrEmpty() {
        publisher.publishBatch((List<QueueMessage>) null);
        publisher.publishBatch(List.of());

        verifyNoInteractions(streamOps);
    }

    @Test
    @DisplayName("publishBatch 多条消息通过 pipeline 批量写入")
    void testPublishBatchWritesAllViaPipeline() {
        QueueMessage m1 = QueueMessage.of("msg-1");
        QueueMessage m2 = QueueMessage.of("msg-2");
        QueueMessage m3 = QueueMessage.of("msg-3");

        publisher.publishBatch(Arrays.asList(m1, m2, m3));

        // pipeline 通过 executePipelined 执行，内部调用 3 次 add
        verify(redisTemplate).executePipelined(any(SessionCallback.class));
    }

    @Test
    @DisplayName("publishBatch 跳过 null 消息")
    void testPublishBatchSkipsNullMessages() {
        QueueMessage m1 = QueueMessage.of("msg-1");
        QueueMessage m3 = QueueMessage.of("msg-3");

        publisher.publishBatch(Arrays.asList(m1, null, m3));

        verify(redisTemplate).executePipelined(any(SessionCallback.class));
    }

    @Test
    @DisplayName("getChannel 返回构造时配置的通道名称")
    void testGetChannel() {
        assertEquals("test-stream", publisher.getChannel());
    }
}
