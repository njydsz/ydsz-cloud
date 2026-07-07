package com.njydsz.pmis.message.producer;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * {@link RocketMQMessageProducer} 单元测试。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("RocketMQMessageProducer 生产者测试")
@ExtendWith(MockitoExtension.class)
class RocketMQMessageProducerTest {

    @Mock
    private RocketMQTemplate rocketMQTemplate;

    @InjectMocks
    private RocketMQMessageProducer producer;

    @Test
    @DisplayName("syncSend 成功返回 msgId,自动填充 messageId")
    void syncSendShouldReturnMsgId() {
        MessageRequest req = new MessageRequest();
        req.setChannel("SMS");
        req.setReceiver("u1");
        SendResult result = new SendResult();
        result.setSendStatus(SendStatus.SEND_OK);
        result.setMsgId("rmq-msg-1");
        when(rocketMQTemplate.syncSend(anyString(), anyString())).thenReturn(result);

        String msgId = producer.syncSend(req);

        assertEquals("rmq-msg-1", msgId);
        assertNotNull(req.getMessageId());
    }

    @Test
    @DisplayName("syncSend 发送异常抛 RuntimeException")
    void syncSendShouldThrowOnFailure() {
        MessageRequest req = new MessageRequest();
        req.setChannel("SMS");
        req.setMessageId("m1");
        when(rocketMQTemplate.syncSend(anyString(), anyString())).thenThrow(new RuntimeException("broker down"));

        assertThrows(RuntimeException.class, () -> producer.syncSend(req));
    }

    @Test
    @DisplayName("syncSend 状态非 SEND_OK 抛异常")
    void syncSendShouldThrowWhenStatusNotOk() {
        MessageRequest req = new MessageRequest();
        req.setChannel("SMS");
        req.setMessageId("m1");
        SendResult result = new SendResult();
        result.setSendStatus(SendStatus.FLUSH_DISK_TIMEOUT);
        when(rocketMQTemplate.syncSend(anyString(), anyString())).thenReturn(result);

        assertThrows(RuntimeException.class, () -> producer.syncSend(req));
    }

    @Test
    @DisplayName("asyncSend 提交不抛异常")
    void asyncSendShouldSubmitWithoutThrowing() {
        MessageRequest req = new MessageRequest();
        req.setChannel("SMS");
        req.setMessageId("m1");
        org.mockito.Mockito.doNothing()
                .when(rocketMQTemplate)
                .asyncSend(anyString(), anyString(), org.mockito.ArgumentMatchers.any());

        producer.asyncSend(req); // 不抛异常
    }
}
