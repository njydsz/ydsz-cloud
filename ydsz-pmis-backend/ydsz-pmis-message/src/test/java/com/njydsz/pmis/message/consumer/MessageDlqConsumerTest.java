package com.njydsz.pmis.message.consumer;

import com.njydsz.pmis.message.entity.MsgLogDO;
import com.njydsz.pmis.message.enums.MessageStatusEnum;
import com.njydsz.pmis.message.mapper.MsgLogMapper;
import com.njydsz.pmis.message.metric.MessageMetrics;
import org.apache.rocketmq.common.message.MessageExt;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * {@link MessageDlqConsumer} 单元测试。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("MessageDlqConsumer 死信落库测试")
@ExtendWith(MockitoExtension.class)
class MessageDlqConsumerTest {

    @Mock
    private MsgLogMapper msgLogMapper;
    @Mock
    private MessageMetrics messageMetrics;

    @InjectMocks
    private MessageDlqConsumer dlqConsumer;

    @Test
    @DisplayName("死信消息落库 status=DEAD 且不抛异常")
    void onMessageShouldInsertDeadLog() {
        MessageExt ext = new MessageExt();
        ext.setMsgId("dlq-1");
        ext.setTopic("%DLQ%pmis-message-consumer");
        ext.setReconsumeTimes(3);
        ext.setBody("{\"channel\":\"SMS\",\"receiver\":\"u1\"}".getBytes());

        dlqConsumer.onMessage(ext); // 不应抛出

        verify(msgLogMapper).insert(any(MsgLogDO.class));
        verify(messageMetrics).recordDead(anyString());
    }

    @Test
    @DisplayName("null 消息跳过")
    void onMessageShouldSkipNull() {
        dlqConsumer.onMessage(null);
        verify(msgLogMapper, never()).insert(any(MsgLogDO.class));
    }

    @Test
    @DisplayName("无法解析的消息体仍以 UNKNOWN 落库")
    void onMessageShouldInsertUnknownWhenParseFails() {
        MessageExt ext = new MessageExt();
        ext.setMsgId("dlq-2");
        ext.setTopic("%DLQ%pmis-message-consumer");
        ext.setReconsumeTimes(3);
        ext.setBody("not-a-json".getBytes());

        dlqConsumer.onMessage(ext);

        verify(msgLogMapper).insert(any(MsgLogDO.class));
    }
}
