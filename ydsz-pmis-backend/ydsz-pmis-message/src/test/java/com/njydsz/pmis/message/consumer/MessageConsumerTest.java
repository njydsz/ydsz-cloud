package com.njydsz.pmis.message.consumer;

import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.common.feign.MessageRequest;
import com.njydsz.pmis.common.util.JsonUtils;
import com.njydsz.pmis.message.entity.MsgLogDO;
import com.njydsz.pmis.message.mapper.MsgLogMapper;
import com.njydsz.pmis.message.service.MessageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link MessageConsumer} 单元测试。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("MessageConsumer 消费幂等测试")
@ExtendWith(MockitoExtension.class)
class MessageConsumerTest {

    @Mock
    private MessageService messageService;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private MsgLogMapper msgLogMapper;

    @InjectMocks
    private MessageConsumer messageConsumer;

    @Test
    @DisplayName("幂等加锁成功后调用 send")
    @SuppressWarnings("unchecked")
    void onMessageShouldSendWhenLockAcquired() {
        MessageRequest req = new MessageRequest();
        req.setChannel("SMS");
        req.setMessageId("m1");
        req.setReceiver("u1");
        String body = JsonUtils.toJson(req);
        ValueOperations<String, String> ops = org.mockito.Mockito.mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(ops);
        when(ops.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);

        messageConsumer.onMessage(body);

        verify(messageService).send(any(MessageRequest.class));
    }

    @Test
    @DisplayName("幂等锁未获取(重复消息)跳过 send")
    @SuppressWarnings("unchecked")
    void onMessageShouldSkipWhenLockNotAcquired() {
        MessageRequest req = new MessageRequest();
        req.setChannel("SMS");
        req.setMessageId("m1");
        String body = JsonUtils.toJson(req);
        ValueOperations<String, String> ops = org.mockito.Mockito.mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(ops);
        when(ops.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(false);

        messageConsumer.onMessage(body);

        verify(messageService, never()).send(any(MessageRequest.class));
    }

    @Test
    @DisplayName("BizException 落库 FAILED 不抛出")
    @SuppressWarnings("unchecked")
    void onMessageShouldRecordFailedOnBizException() {
        MessageRequest req = new MessageRequest();
        req.setChannel("SMS");
        req.setMessageId("m1");
        req.setReceiver("u1");
        String body = JsonUtils.toJson(req);
        ValueOperations<String, String> ops = org.mockito.Mockito.mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(ops);
        when(ops.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        when(messageService.send(any(MessageRequest.class))).thenThrow(new BizException("rate limit"));

        messageConsumer.onMessage(body); // 不应抛出

        verify(msgLogMapper).insert(any(MsgLogDO.class));
    }

    @Test
    @DisplayName("系统异常释放锁并抛出触发重投")
    @SuppressWarnings("unchecked")
    void onMessageShouldThrowOnSystemException() {
        MessageRequest req = new MessageRequest();
        req.setChannel("SMS");
        req.setMessageId("m1");
        req.setReceiver("u1");
        String body = JsonUtils.toJson(req);
        ValueOperations<String, String> ops = org.mockito.Mockito.mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(ops);
        when(ops.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);
        when(messageService.send(any(MessageRequest.class))).thenThrow(new RuntimeException("system"));

        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
                () -> messageConsumer.onMessage(body));
        verify(redisTemplate).execute(any(), any(java.util.List.class), anyString());
    }

    @Test
    @DisplayName("空消息体直接跳过")
    void onMessageShouldSkipEmptyBody() {
        messageConsumer.onMessage("");
        verify(messageService, never()).send(any(MessageRequest.class));
    }
}
