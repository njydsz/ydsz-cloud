package com.njydsz.common.queue.mq.rabbit;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link RabbitMQProperties} 配置解析测试。
 *
 * <p>验证 RabbitMQ 队列配置的默认值、解析方法（resolved*）以及边界情况。
 * 由于 {@link com.njydsz.common.queue.mq.rabbit.RabbitMQPublisher} 在构造时
 * 直接建立 AMQP 连接，无法在单元测试中验证发布行为；这里通过验证配置解析
 * 保证发布者构造前的配置正确性。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@DisplayName("RabbitMQProperties 配置测试")
class RabbitMQPropertiesTest {

    private RabbitMQProperties properties;

    @BeforeEach
    void setUp() {
        properties = new RabbitMQProperties();
    }

    @Test
    @DisplayName("默认值符合预期")
    void testDefaultValues() {
        assertEquals("localhost", properties.getHost());
        assertEquals(5672, properties.getRabbitPort());
        assertEquals("guest", properties.getUsername());
        assertEquals("guest", properties.getPassword());
        assertEquals("/", properties.getVirtualHost());
        assertEquals("ydsz-rabbitmq-queue", properties.getQueueName());
        assertEquals("ydsz-exchange", properties.getExchangeName());
        assertEquals("ydsz.routing.key", properties.getRoutingKey());
        assertTrue(properties.isAcknowledgeMode());
        assertEquals(10, properties.getPrefetchCount());
        assertTrue(properties.isDurable());
        assertEquals(5, properties.getConcurrentConsumers());
        assertEquals(10, properties.getMaxConcurrentConsumers());
    }

    @Test
    @DisplayName("resolvedHost 显式配置时返回配置值")
    void testResolvedHostExplicit() {
        properties.setHost("rabbitmq.prod.local");
        assertEquals("rabbitmq.prod.local", properties.resolvedHost());
    }

    @Test
    @DisplayName("resolvedHost null/空时返回默认值 'localhost'")
    void testResolvedHostDefaults() {
        properties.setHost(null);
        assertEquals("localhost", properties.resolvedHost());

        properties.setHost("");
        assertEquals("localhost", properties.resolvedHost());

        properties.setHost("   ");
        assertEquals("localhost", properties.resolvedHost());
    }

    @Test
    @DisplayName("resolvedPort 正数时返回配置值")
    void testResolvedPortPositive() {
        properties.setRabbitPort(5673);
        assertEquals(5673, properties.resolvedPort());
    }

    @Test
    @DisplayName("resolvedPort 0 或负数时返回默认值 5672")
    void testResolvedPortNonPositiveDefaultsTo5672() {
        properties.setRabbitPort(0);
        assertEquals(5672, properties.resolvedPort());

        properties.setRabbitPort(-1);
        assertEquals(5672, properties.resolvedPort());
    }

    @Test
    @DisplayName("resolvedUsername 显式配置时返回配置值")
    void testResolvedUsernameExplicit() {
        properties.setUsername("admin");
        assertEquals("admin", properties.resolvedUsername());
    }

    @Test
    @DisplayName("resolvedUsername null/空时返回默认值 'guest'")
    void testResolvedUsernameDefaults() {
        properties.setUsername(null);
        assertEquals("guest", properties.resolvedUsername());

        properties.setUsername("");
        assertEquals("guest", properties.resolvedUsername());
    }

    @Test
    @DisplayName("resolvedPassword 直接返回原始值（不回退到默认弱密码）")
    void testResolvedPasswordReturnsRawValue() {
        // 显式密码
        properties.setPassword("strong-password");
        assertEquals("strong-password", properties.resolvedPassword());

        // 空密码直接返回空，由连接层报错
        properties.setPassword("");
        assertEquals("", properties.resolvedPassword());

        // null 直接返回 null
        properties.setPassword(null);
        assertNull(properties.resolvedPassword());
    }

    @Test
    @DisplayName("resolvedVirtualHost 显式配置时返回配置值")
    void testResolvedVirtualHostExplicit() {
        properties.setVirtualHost("/production");
        assertEquals("/production", properties.resolvedVirtualHost());
    }

    @Test
    @DisplayName("resolvedVirtualHost null/空时返回默认值 '/'")
    void testResolvedVirtualHostDefaults() {
        properties.setVirtualHost(null);
        assertEquals("/", properties.resolvedVirtualHost());

        properties.setVirtualHost("");
        assertEquals("/", properties.resolvedVirtualHost());
    }

    @Test
    @DisplayName("resolvedQueueName 显式配置时返回配置值")
    void testResolvedQueueNameExplicit() {
        properties.setQueueName("my-queue");
        assertEquals("my-queue", properties.resolvedQueueName());
    }

    @Test
    @DisplayName("resolvedQueueName null/空时返回默认值")
    void testResolvedQueueNameDefaults() {
        properties.setQueueName(null);
        assertEquals("ydsz-rabbitmq-queue", properties.resolvedQueueName());

        properties.setQueueName("");
        assertEquals("ydsz-rabbitmq-queue", properties.resolvedQueueName());
    }

    @Test
    @DisplayName("配置 setter/getter 正常工作")
    void testSettersAndGetters() {
        properties.setHost("rabbit.example.com");
        properties.setRabbitPort(5673);
        properties.setUsername("producer");
        properties.setPassword("s3cr3t");
        properties.setVirtualHost("/vhost");
        properties.setQueueName("orders");
        properties.setExchangeName("order-exchange");
        properties.setRoutingKey("order.created");
        properties.setAcknowledgeMode(false);
        properties.setPrefetchCount(50);
        properties.setDurable(false);
        properties.setConcurrentConsumers(8);
        properties.setMaxConcurrentConsumers(20);

        assertEquals("rabbit.example.com", properties.getHost());
        assertEquals(5673, properties.getRabbitPort());
        assertEquals("producer", properties.getUsername());
        assertEquals("s3cr3t", properties.getPassword());
        assertEquals("/vhost", properties.getVirtualHost());
        assertEquals("orders", properties.getQueueName());
        assertEquals("order-exchange", properties.getExchangeName());
        assertEquals("order.created", properties.getRoutingKey());
        assertFalse(properties.isAcknowledgeMode());
        assertEquals(50, properties.getPrefetchCount());
        assertFalse(properties.isDurable());
        assertEquals(8, properties.getConcurrentConsumers());
        assertEquals(20, properties.getMaxConcurrentConsumers());
    }

    @Test
    @DisplayName("继承 QueueProperties 的字段同样可用")
    void testInheritedQueuePropertiesFields() {
        properties.setEnabled(false);
        properties.setHost("rabbit-host");
        properties.setPort(5672);

        assertFalse(properties.isEnabled());
        assertEquals("rabbit-host", properties.getHost());
        assertEquals(5672, properties.getPort());
    }
}
