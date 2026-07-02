package com.njydsz.pmis.message.config;

import com.njydsz.pmis.message.consumer.MessageConsumer;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RocketMQ 配置验证测试（P1-12）
 *
 * <p>验证内容：
 * <ol>
 *   <li>application.yml 中 RocketMQ 配置项完整（name-server、producer.group、send-message-timeout、consumer.enabled）</li>
 *   <li>MessageConsumer 的 @RocketMQMessageListener 注解参数正确（topic、consumerGroup、selectorExpression、maxReconsumeTimes）</li>
 *   <li>MessageConsumer 的条件注解正确（@ConditionalOnClass、@ConditionalOnProperty）</li>
 * </ol>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("RocketMQ 配置验证测试")
class RocketMQConfigTest {

    private static final String APPLICATION_YML_PATH = "/application.yml";

    // ==================== application.yml 配置项验证 ====================

    @Test
    @DisplayName("application.yml 包含 rocketmq.name-server 配置项")
    void yml_shouldContainNameServer() throws IOException {
        String ymlContent = readApplicationYml();
        assertThat(ymlContent)
                .as("application.yml 必须包含 rocketmq.name-server 配置")
                .contains("name-server:")
                .contains("${ROCKETMQ_NAME_SERVER:127.0.0.1:9876}");
    }

    @Test
    @DisplayName("application.yml 包含 rocketmq.producer.group 配置项")
    void yml_shouldContainProducerGroup() throws IOException {
        String ymlContent = readApplicationYml();
        assertThat(ymlContent)
                .as("application.yml 必须包含 rocketmq.producer.group 配置")
                .contains("group:")
                .contains("${ROCKETMQ_PRODUCER_GROUP:pmis-producer-group}");
    }

    @Test
    @DisplayName("application.yml 包含 rocketmq.producer.send-message-timeout 配置项")
    void yml_shouldContainSendMessageTimeout() throws IOException {
        String ymlContent = readApplicationYml();
        assertThat(ymlContent)
                .as("application.yml 必须包含 send-message-timeout: 5000")
                .contains("send-message-timeout: 5000");
    }

    @Test
    @DisplayName("application.yml 包含 rocketmq.consumer.enabled 配置项，默认值为 true")
    void yml_shouldContainConsumerEnabled() throws IOException {
        String ymlContent = readApplicationYml();
        assertThat(ymlContent)
                .as("application.yml 必须包含 rocketmq.consumer.enabled 配置，默认启用消费者")
                .contains("consumer:")
                .contains("enabled:")
                .contains("${ROCKETMQ_CONSUMER_ENABLED:true}");
    }

    @Test
    @DisplayName("application.yml 包含完整的 rocketmq 顶层配置块")
    void yml_shouldContainRocketMQBlock() throws IOException {
        String ymlContent = readApplicationYml();
        assertThat(ymlContent)
                .as("application.yml 必须包含 rocketmq 顶层配置块")
                .containsPattern("(?m)^rocketmq:");
    }

    // ==================== MessageConsumer 注解验证 ====================

    @Test
    @DisplayName("MessageConsumer 标注 @RocketMQMessageListener，topic = pmis-message-topic")
    void consumer_shouldHaveCorrectTopic() {
        RocketMQMessageListener annotation = MessageConsumer.class.getAnnotation(RocketMQMessageListener.class);
        assertThat(annotation)
                .as("MessageConsumer 必须标注 @RocketMQMessageListener")
                .isNotNull();
        assertThat(annotation.topic())
                .as("topic 必须为 pmis-message-topic")
                .isEqualTo("pmis-message-topic");
    }

    @Test
    @DisplayName("MessageConsumer 的 consumerGroup = pmis-message-consumer")
    void consumer_shouldHaveCorrectConsumerGroup() {
        RocketMQMessageListener annotation = MessageConsumer.class.getAnnotation(RocketMQMessageListener.class);
        assertThat(annotation)
                .as("MessageConsumer 必须标注 @RocketMQMessageListener")
                .isNotNull();
        assertThat(annotation.consumerGroup())
                .as("consumerGroup 必须为 pmis-message-consumer")
                .isEqualTo("pmis-message-consumer");
    }

    @Test
    @DisplayName("MessageConsumer 的 selectorExpression = *（订阅所有 tag）")
    void consumer_shouldHaveWildcardSelector() {
        RocketMQMessageListener annotation = MessageConsumer.class.getAnnotation(RocketMQMessageListener.class);
        assertThat(annotation)
                .as("MessageConsumer 必须标注 @RocketMQMessageListener")
                .isNotNull();
        assertThat(annotation.selectorExpression())
                .as("selectorExpression 必须为 *（订阅所有 tag）")
                .isEqualTo("*");
    }

    @Test
    @DisplayName("MessageConsumer 的 maxReconsumeTimes = 3（最大重试 3 次）")
    void consumer_shouldHaveMaxReconsumeTimes3() {
        RocketMQMessageListener annotation = MessageConsumer.class.getAnnotation(RocketMQMessageListener.class);
        assertThat(annotation)
                .as("MessageConsumer 必须标注 @RocketMQMessageListener")
                .isNotNull();
        assertThat(annotation.maxReconsumeTimes())
                .as("maxReconsumeTimes 必须为 3")
                .isEqualTo(3);
    }

    // ==================== 条件注解验证 ====================

    @Test
    @DisplayName("MessageConsumer 标注 @ConditionalOnClass，检查 RocketMQMessageListener 类存在")
    void consumer_shouldHaveConditionalOnClass() {
        ConditionalOnClass annotation = MessageConsumer.class.getAnnotation(ConditionalOnClass.class);
        assertThat(annotation)
                .as("MessageConsumer 必须标注 @ConditionalOnClass")
                .isNotNull();
        assertThat(annotation.name())
                .as("@ConditionalOnClass 必须检查 org.apache.rocketmq.spring.annotation.RocketMQMessageListener")
                .contains("org.apache.rocketmq.spring.annotation.RocketMQMessageListener");
    }

    @Test
    @DisplayName("MessageConsumer 标注 @ConditionalOnProperty(prefix=rocketmq.consumer, name=enabled)")
    void consumer_shouldHaveConditionalOnProperty() {
        ConditionalOnProperty annotation = MessageConsumer.class.getAnnotation(ConditionalOnProperty.class);
        assertThat(annotation)
                .as("MessageConsumer 必须标注 @ConditionalOnProperty")
                .isNotNull();
        assertThat(annotation.prefix())
                .as("prefix 必须为 rocketmq.consumer")
                .isEqualTo("rocketmq.consumer");
        assertThat(annotation.name())
                .as("name 必须包含 enabled")
                .contains("enabled");
        assertThat(annotation.havingValue())
                .as("havingValue 必须为 true")
                .isEqualTo("true");
    }

    // ==================== 环境变量覆盖验证 ====================

    @Test
    @DisplayName("ROCKETMQ_NAME_SERVER 环境变量可覆盖默认 name-server 地址")
    void envVar_shouldOverrideNameServer() throws IOException {
        String ymlContent = readApplicationYml();
        assertThat(ymlContent)
                .as("name-server 应使用 ${ROCKETMQ_NAME_SERVER:...} 占位符以支持环境变量覆盖")
                .contains("${ROCKETMQ_NAME_SERVER:");
    }

    @Test
    @DisplayName("ROCKETMQ_PRODUCER_GROUP 环境变量可覆盖默认 producer group")
    void envVar_shouldOverrideProducerGroup() throws IOException {
        String ymlContent = readApplicationYml();
        assertThat(ymlContent)
                .as("producer.group 应使用 ${ROCKETMQ_PRODUCER_GROUP:...} 占位符以支持环境变量覆盖")
                .contains("${ROCKETMQ_PRODUCER_GROUP:");
    }

    // ==================== 辅助方法 ====================

    /**
     * 读取 application.yml 文件内容。
     *
     * @return application.yml 文件文本
     * @throws IOException 读取失败时抛出
     */
    private String readApplicationYml() throws IOException {
        try (InputStream is = getClass().getResourceAsStream(APPLICATION_YML_PATH)) {
            assertThat(is)
                    .as("application.yml 必须存在于 classpath 中")
                    .isNotNull();
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
