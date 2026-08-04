package com.remisoft.common.queue.mq.kafka;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link KafkaQueueProperties} 配置解析测试。
 *
 * <p>验证 Kafka 队列配置的默认值、解析方法（resolved*）以及边界情况。
 * 由于 {@link KafkaMessagePublisher} 在构造时直接创建 {@code KafkaProducer}
 * 连接真实 Kafka 集群，无法在单元测试中验证发布行为；这里通过验证配置解析
 * 保证发布者/订阅者构造前的配置正确性。
 *
 * @author remi-team
 * @since 1.0.0
 */
@DisplayName("KafkaQueueProperties 配置测试")
class KafkaQueuePropertiesTest {

    private KafkaQueueProperties properties;

    @BeforeEach
    void setUp() {
        properties = new KafkaQueueProperties();
    }

    @Test
    @DisplayName("默认值符合预期")
    void testDefaultValues() {
        assertEquals("localhost:9092", properties.getBootstrapServers());
        assertEquals("remi-consumer-group", properties.getGroupId());
        assertEquals("remi-kafka-topic", properties.getTopic());
        assertFalse(properties.isEnableAutoCommit());
        assertEquals("earliest", properties.getAutoOffsetReset());
        assertEquals(10, properties.getMaxPollRecords());
        assertEquals(30000, properties.getSessionTimeoutMs());
        assertEquals(10000, properties.getHeartbeatIntervalMs());
        assertEquals("org.apache.kafka.common.serialization.StringSerializer",
                properties.getKeySerializer());
        assertEquals("org.apache.kafka.common.serialization.StringSerializer",
                properties.getValueSerializer());
        assertEquals("org.apache.kafka.common.serialization.StringDeserializer",
                properties.getKeyDeserializer());
        assertEquals("org.apache.kafka.common.serialization.StringDeserializer",
                properties.getValueDeserializer());
    }

    @Test
    @DisplayName("resolvedBootstrapServers 显式配置时返回配置值")
    void testResolvedBootstrapServersExplicit() {
        properties.setBootstrapServers("kafka-broker:9092,kafka-broker2:9092");

        assertEquals("kafka-broker:9092,kafka-broker2:9092", properties.resolvedBootstrapServers());
    }

    @Test
    @DisplayName("resolvedBootstrapServers null 时返回默认值")
    void testResolvedBootstrapServersNull() {
        properties.setBootstrapServers(null);

        assertEquals("localhost:9092", properties.resolvedBootstrapServers());
    }

    @Test
    @DisplayName("resolvedBootstrapServers 空字符串时返回默认值")
    void testResolvedBootstrapServersEmpty() {
        properties.setBootstrapServers("");

        assertEquals("localhost:9092", properties.resolvedBootstrapServers());
    }

    @Test
    @DisplayName("resolvedBootstrapServers 纯空白字符时返回默认值")
    void testResolvedBootstrapServersBlank() {
        properties.setBootstrapServers("   ");

        assertEquals("localhost:9092", properties.resolvedBootstrapServers());
    }

    @Test
    @DisplayName("resolvedGroupId 显式配置时返回配置值")
    void testResolvedGroupIdExplicit() {
        properties.setGroupId("my-group");

        assertEquals("my-group", properties.resolvedGroupId());
    }

    @Test
    @DisplayName("resolvedGroupId null/空时返回默认值")
    void testResolvedGroupIdDefaults() {
        properties.setGroupId(null);
        assertEquals("remi-consumer-group", properties.resolvedGroupId());

        properties.setGroupId("");
        assertEquals("remi-consumer-group", properties.resolvedGroupId());
    }

    @Test
    @DisplayName("resolvedTopic 显式配置时返回配置值")
    void testResolvedTopicExplicit() {
        properties.setTopic("my-topic");

        assertEquals("my-topic", properties.resolvedTopic());
    }

    @Test
    @DisplayName("resolvedTopic null/空时返回默认值")
    void testResolvedTopicDefaults() {
        properties.setTopic(null);
        assertEquals("remi-kafka-topic", properties.resolvedTopic());

        properties.setTopic("");
        assertEquals("remi-kafka-topic", properties.resolvedTopic());
    }

    @Test
    @DisplayName("配置 setter/getter 正常工作")
    void testSettersAndGetters() {
        properties.setBootstrapServers("prod-kafka:9092");
        properties.setGroupId("prod-group");
        properties.setTopic("prod-topic");
        properties.setEnableAutoCommit(true);
        properties.setAutoOffsetReset("latest");
        properties.setMaxPollRecords(100);
        properties.setSessionTimeoutMs(45000);
        properties.setHeartbeatIntervalMs(15000);

        assertEquals("prod-kafka:9092", properties.getBootstrapServers());
        assertEquals("prod-group", properties.getGroupId());
        assertEquals("prod-topic", properties.getTopic());
        assertTrue(properties.isEnableAutoCommit());
        assertEquals("latest", properties.getAutoOffsetReset());
        assertEquals(100, properties.getMaxPollRecords());
        assertEquals(45000, properties.getSessionTimeoutMs());
        assertEquals(15000, properties.getHeartbeatIntervalMs());
    }

    @Test
    @DisplayName("继承 QueueProperties 的字段同样可用")
    void testInheritedQueuePropertiesFields() {
        properties.setEnabled(false);
        properties.setHost("kafka-host");
        properties.setPort(9094);

        assertFalse(properties.isEnabled());
        assertEquals("kafka-host", properties.getHost());
        assertEquals(9094, properties.getPort());
    }
}
