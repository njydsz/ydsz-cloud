package com.njydsz.pmis.system.consumer;

import com.alibaba.fastjson2.JSON;
import com.njydsz.pmis.common.constant.PmisMessageTopics;
import com.njydsz.pmis.common.feign.MessageRequest;
import com.njydsz.pmis.system.entity.MessageLogDO;
import com.njydsz.pmis.system.mapper.MessageLogMapper;
import org.apache.rocketmq.common.message.MessageExt;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 死信队列消费者单元测试（P0-D3）
 *
 * <p>验证死信消息处理策略：
 * <ul>
 *   <li>正常死信消息：解析 → 落库 status=DEAD → 不抛异常</li>
 *   <li>消息体解析失败：仍落库（channel=UNKNOWN）→ 不抛异常</li>
 *   <li>null 消息：跳过</li>
 *   <li>落库失败：仅记录日志，不抛异常（避免 DLQ 循环重投）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("死信队列消费者测试 - P0-D3")
class MessageDlqConsumerTest {

    @Mock
    private MessageLogMapper messageLogMapper;

    @InjectMocks
    private MessageDlqConsumer dlqConsumer;

    @Test
    @DisplayName("onMessage - 正常死信消息应落库 status=DEAD 且不抛异常")
    void onMessage_normalDlq_shouldInsertDeadLogAndNoThrow() {
        // Arrange
        MessageRequest request = new MessageRequest();
        request.setChannel("EMAIL");
        request.setTemplateCode("BUDGET_ALERT");
        request.setReceiver("pmo@njydsz.com");
        request.setBizType("alert");
        request.setBizId("BIZ-001");
        request.setMessageId("msg-uuid-001");
        byte[] body = JSON.toJSONBytes(request);

        MessageExt msg = new MessageExt();
        msg.setMsgId("MQ-MSG-ID-001");
        msg.setReconsumeTimes(3);
        msg.setBody(body);
        msg.setTopic(PmisMessageTopics.TOPIC_MESSAGE);

        // Act
        // Assert - 不抛异常（避免 DLQ 循环重投）
        assertDoesNotThrow(() -> dlqConsumer.onMessage(msg));

        // Verify - 落库 status=DEAD
        ArgumentCaptor<MessageLogDO> captor = ArgumentCaptor.forClass(MessageLogDO.class);
        verify(messageLogMapper, times(1)).insert(captor.capture());
        MessageLogDO logDO = captor.getValue();
        assertEquals("DEAD", logDO.getStatus());
        assertEquals("EMAIL", logDO.getChannel());
        assertEquals("pmo@njydsz.com", logDO.getReceiver());
        assertEquals("msg-uuid-001", logDO.getMsgId());
        assertEquals(PmisMessageTopics.TOPIC_MESSAGE, logDO.getTopic());
        assertEquals(3, logDO.getReconsumeTimes());
        // errorMessage 包含 MQ 元信息
        assertNotNull(logDO.getErrorMessage());
        assertTrue(logDO.getErrorMessage().contains("MQ-MSG-ID-001"));
        assertTrue(logDO.getErrorMessage().contains("reconsumeTimes=3"));
    }

    @Test
    @DisplayName("onMessage - 消息体解析失败应仍落库 channel=UNKNOWN 且不抛异常")
    void onMessage_unparseableBody_shouldInsertUnknownAndNoThrow() {
        // Arrange
        MessageExt msg = new MessageExt();
        msg.setMsgId("MQ-MSG-ID-002");
        msg.setReconsumeTimes(3);
        msg.setBody("invalid json {{".getBytes(StandardCharsets.UTF_8));
        msg.setTopic(PmisMessageTopics.TOPIC_MESSAGE);

        // Act + Assert
        assertDoesNotThrow(() -> dlqConsumer.onMessage(msg));

        // Verify
        ArgumentCaptor<MessageLogDO> captor = ArgumentCaptor.forClass(MessageLogDO.class);
        verify(messageLogMapper, times(1)).insert(captor.capture());
        MessageLogDO logDO = captor.getValue();
        assertEquals("DEAD", logDO.getStatus());
        assertEquals("UNKNOWN", logDO.getChannel());
        assertEquals("UNKNOWN", logDO.getReceiver());
        assertNotNull(logDO.getContent());
    }

    @Test
    @DisplayName("onMessage - null 消息应跳过且不落库")
    void onMessage_null_shouldSkip() {
        assertDoesNotThrow(() -> dlqConsumer.onMessage(null));
        verifyNoInteractions(messageLogMapper);
    }

    @Test
    @DisplayName("onMessage - 落库失败应仅记录日志，不抛异常")
    void onMessage_insertFailed_shouldNotThrow() {
        // Arrange
        MessageRequest request = new MessageRequest();
        request.setChannel("SMS");
        request.setReceiver("13800138000");
        request.setMessageId("msg-uuid-003");
        MessageExt msg = new MessageExt();
        msg.setMsgId("MQ-MSG-ID-003");
        msg.setReconsumeTimes(3);
        msg.setBody(JSON.toJSONBytes(request));
        msg.setTopic(PmisMessageTopics.TOPIC_MESSAGE);

        when(messageLogMapper.insert(any(MessageLogDO.class)))
                .thenThrow(new RuntimeException("DB connection lost"));

        // Act + Assert - 落库失败不抛异常，避免 DLQ 循环重投
        assertDoesNotThrow(() -> dlqConsumer.onMessage(msg));
        verify(messageLogMapper, times(1)).insert(any(MessageLogDO.class));
    }

    @Test
    @DisplayName("onMessage - body 为 null 应不抛异常")
    void onMessage_nullBody_shouldNotThrow() {
        MessageExt msg = new MessageExt();
        msg.setMsgId("MQ-MSG-ID-004");
        msg.setReconsumeTimes(3);
        msg.setBody(null);
        msg.setTopic(PmisMessageTopics.TOPIC_MESSAGE);

        assertDoesNotThrow(() -> dlqConsumer.onMessage(msg));
        verify(messageLogMapper, times(1)).insert(any(MessageLogDO.class));
    }
}
