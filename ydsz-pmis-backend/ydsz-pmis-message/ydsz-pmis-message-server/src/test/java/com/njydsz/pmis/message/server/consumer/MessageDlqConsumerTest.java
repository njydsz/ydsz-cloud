package com.njydsz.pmis.message.server.consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.apache.rocketmq.common.message.MessageExt;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.njydsz.pmis.common.feign.MessageRequest;
import com.njydsz.pmis.common.util.json.JsonUtils;
import com.njydsz.pmis.message.domain.entity.core.MsgLogDO;
import com.njydsz.pmis.message.infra.mapper.core.MsgLogMapper;
import com.njydsz.pmis.message.server.metric.MessageMetrics;

/**
 * MessageDlqConsumer 死信消费者单元测试。
 *
 * <p>P0-5: 验证 Redis SET NX EX 幂等去重 + update-then-insert 落库逻辑。
 */
@DisplayName("MessageDlqConsumer 死信消费者测试")
@ExtendWith(MockitoExtension.class)
class MessageDlqConsumerTest {

    @Mock
    private MsgLogMapper msgLogMapper;

    @Mock
    private MessageMetrics messageMetrics;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOps;

    @InjectMocks
    private MessageDlqConsumer consumer;

    private MessageExt buildMessage(String msgId, int reconsumeTimes, String body) {
        MessageExt msg = new MessageExt();
        msg.setMsgId(msgId);
        msg.setReconsumeTimes(reconsumeTimes);
        msg.setTopic("TOPIC_MESSAGE");
        msg.setBody(body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8));
        return msg;
    }

    @Nested
    @DisplayName("幂等去重")
    class IdempotentTest {

        @Test
        @DisplayName("首次处理: SET NX EX 返回 true,继续落库")
        void firstProcessShouldContinue() {
            when(redisTemplate.opsForValue()).thenReturn(valueOps);
            when(valueOps.setIfAbsent(eq("pmis:msg:dlq:idempotent:mq-1"), eq("1"),
                    any(Duration.class))).thenReturn(true);
            when(msgLogMapper.update(eq(null), any(LambdaUpdateWrapper.class))).thenReturn(0);
            MessageExt msg = buildMessage("mq-1", 3, JsonUtils.toJson(new MessageRequest()));

            consumer.onMessage(msg);

            verify(msgLogMapper, times(1)).insert(any(MsgLogDO.class));
            verify(messageMetrics, times(1)).recordDead(any());
        }

        @Test
        @DisplayName("重复消息: SET NX EX 返回 false,跳过处理")
        void duplicateShouldSkip() {
            when(redisTemplate.opsForValue()).thenReturn(valueOps);
            when(valueOps.setIfAbsent(any(), eq("1"), any(Duration.class))).thenReturn(false);
            MessageExt msg = buildMessage("mq-dup", 3, "{}");

            consumer.onMessage(msg);

            verify(msgLogMapper, never()).update(any(), any());
            verify(msgLogMapper, never()).insert(any(MsgLogDO.class));
            verify(messageMetrics, never()).recordDead(any());
        }

        @Test
        @DisplayName("null 消息: 直接跳过")
        void nullMessageShouldSkip() {
            consumer.onMessage(null);

            verify(redisTemplate, never()).opsForValue();
            verify(msgLogMapper, never()).insert(any(MsgLogDO.class));
        }
    }

    @Nested
    @DisplayName("update-then-insert 落库")
    class UpdateThenInsertTest {

        @Test
        @DisplayName("已有记录: update 成功(updated>0)不 insert")
        void updateExistingRecord() {
            when(redisTemplate.opsForValue()).thenReturn(valueOps);
            when(valueOps.setIfAbsent(any(), eq("1"), any(Duration.class))).thenReturn(true);
            when(msgLogMapper.update(eq(null), any(LambdaUpdateWrapper.class))).thenReturn(1);

            MessageRequest req = new MessageRequest();
            req.setMessageId("biz-msg-001");
            req.setChannel("SMS");
            MessageExt msg = buildMessage("mq-1", 3, JsonUtils.toJson(req));

            consumer.onMessage(msg);

            verify(msgLogMapper, never()).insert(any(MsgLogDO.class));
            verify(messageMetrics, times(1)).recordDead("SMS");
        }

        @Test
        @DisplayName("无已有记录: update=0 时 insert 新 DEAD 记录")
        void insertWhenNoExistingRecord() {
            when(redisTemplate.opsForValue()).thenReturn(valueOps);
            when(valueOps.setIfAbsent(any(), eq("1"), any(Duration.class))).thenReturn(true);
            when(msgLogMapper.update(eq(null), any(LambdaUpdateWrapper.class))).thenReturn(0);

            MessageRequest req = new MessageRequest();
            req.setMessageId("biz-msg-002");
            req.setChannel("EMAIL");
            MessageExt msg = buildMessage("mq-2", 5, JsonUtils.toJson(req));

            consumer.onMessage(msg);

            verify(msgLogMapper, times(1)).insert(any(MsgLogDO.class));
            verify(messageMetrics, times(1)).recordDead("EMAIL");
        }

        @Test
        @DisplayName("消息体解析失败: 仍落库 UNKNOWN 通道")
        void parseFailureStillPersist() {
            when(redisTemplate.opsForValue()).thenReturn(valueOps);
            when(valueOps.setIfAbsent(any(), eq("1"), any(Duration.class))).thenReturn(true);
            when(msgLogMapper.update(eq(null), any(LambdaUpdateWrapper.class))).thenReturn(0);

            MessageExt msg = buildMessage("mq-3", 1, "not-a-json");

            consumer.onMessage(msg);

            verify(msgLogMapper, times(1)).insert(any(MsgLogDO.class));
            verify(messageMetrics, times(1)).recordDead("UNKNOWN");
        }
    }
}
