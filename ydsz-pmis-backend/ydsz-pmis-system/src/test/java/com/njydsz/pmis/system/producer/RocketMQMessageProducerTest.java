package com.njydsz.pmis.system.producer;

import com.njydsz.pmis.common.constant.PmisMessageTopics;
import com.njydsz.pmis.common.feign.MessageRequest;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * RocketMQ 消息生产者单元测试（P0-D3）
 *
 * <p>验证生产者封装核心逻辑：
 * <ul>
 *   <li>syncSend 成功：返回 msgId</li>
 *   <li>syncSend 失败：抛 RuntimeException</li>
 *   <li>asyncSend：不抛异常（结果通过回调）</li>
 *   <li>messageId 为空时自动生成 UUID</li>
 *   <li>request 为 null 抛 IllegalArgumentException</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RocketMQ 消息生产者测试 - P0-D3")
class RocketMQMessageProducerTest {

    @Mock
    private RocketMQTemplate rocketMQTemplate;

    @InjectMocks
    private RocketMQMessageProducer producer;

    @Test
    @DisplayName("syncSend - 成功应返回 msgId 且自动填充 messageId")
    void syncSend_success_shouldReturnMsgId() {
        // Arrange
        MessageRequest request = new MessageRequest();
        request.setChannel("EMAIL");
        request.setReceiver("pmo@njydsz.com");
        request.setTemplateCode("BUDGET_ALERT");
        // messageId 故意留空，验证自动填充

        SendResult sendResult = new SendResult();
        sendResult.setSendStatus(SendStatus.SEND_OK);
        sendResult.setMsgId("MQ-MSG-ID-001");
        when(rocketMQTemplate.syncSend(eq(PmisMessageTopics.TOPIC_MESSAGE), any(String.class)))
                .thenReturn(sendResult);

        // Act
        String msgId = producer.syncSend(request);

        // Assert
        assertEquals("MQ-MSG-ID-001", msgId);
        // messageId 应被自动填充
        assertNotNull(request.getMessageId());
        assertFalse(request.getMessageId().isBlank());
    }

    @Test
    @DisplayName("syncSend - 已有 messageId 时不覆盖")
    void syncSend_existingMessageId_shouldNotOverwrite() {
        MessageRequest request = new MessageRequest();
        request.setChannel("SMS");
        request.setReceiver("13800138000");
        request.setMessageId("custom-msg-id");

        SendResult sendResult = new SendResult();
        sendResult.setSendStatus(SendStatus.SEND_OK);
        sendResult.setMsgId("MQ-MSG-ID-002");
        when(rocketMQTemplate.syncSend(eq(PmisMessageTopics.TOPIC_MESSAGE), any(String.class)))
                .thenReturn(sendResult);

        producer.syncSend(request);

        assertEquals("custom-msg-id", request.getMessageId());
    }

    @Test
    @DisplayName("syncSend - RocketMQ 抛异常应包装为 RuntimeException")
    void syncSend_mqException_shouldWrapAsRuntimeException() {
        MessageRequest request = new MessageRequest();
        request.setChannel("EMAIL");
        request.setReceiver("test@njydsz.com");
        request.setMessageId("msg-001");

        when(rocketMQTemplate.syncSend(any(String.class), any(String.class)))
                .thenThrow(new RuntimeException("MQ connection lost"));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> producer.syncSend(request));
        assertTrue(ex.getMessage().contains("RocketMQ syncSend failed"));
    }

    @Test
    @DisplayName("syncSend - SendStatus 非 SEND_OK 应抛 RuntimeException")
    void syncSend_statusNotOk_shouldThrow() {
        MessageRequest request = new MessageRequest();
        request.setChannel("EMAIL");
        request.setReceiver("test@njydsz.com");
        request.setMessageId("msg-002");

        SendResult sendResult = new SendResult();
        sendResult.setSendStatus(SendStatus.FLUSH_DISK_TIMEOUT);
        sendResult.setMsgId("MQ-MSG-ID-003");
        when(rocketMQTemplate.syncSend(eq(PmisMessageTopics.TOPIC_MESSAGE), any(String.class)))
                .thenReturn(sendResult);

        RuntimeException ex = assertThrows(RuntimeException.class, () -> producer.syncSend(request));
        assertTrue(ex.getMessage().contains("FLUSH_DISK_TIMEOUT"));
    }

    @Test
    @DisplayName("syncSend - request 为 null 应抛 IllegalArgumentException")
    void syncSend_nullRequest_shouldThrow() {
        assertThrows(IllegalArgumentException.class, () -> producer.syncSend(null));
    }

    @Test
    @DisplayName("asyncSend - 正常提交不抛异常")
    void asyncSend_normal_shouldNotThrow() {
        MessageRequest request = new MessageRequest();
        request.setChannel("PUSH");
        request.setReceiver("device-001");
        // messageId 留空，验证自动填充

        // asyncSend 无返回值，仅验证不抛异常 + 调用了 rocketMQTemplate.asyncSend
        assertDoesNotThrow(() -> producer.asyncSend(request));

        verify(rocketMQTemplate, times(1))
                .asyncSend(eq(PmisMessageTopics.TOPIC_MESSAGE), any(String.class), any());
        // messageId 应被自动填充
        assertNotNull(request.getMessageId());
    }

    @Test
    @DisplayName("asyncSend - request 为 null 应抛 IllegalArgumentException")
    void asyncSend_nullRequest_shouldThrow() {
        assertThrows(IllegalArgumentException.class, () -> producer.asyncSend(null));
    }
}
