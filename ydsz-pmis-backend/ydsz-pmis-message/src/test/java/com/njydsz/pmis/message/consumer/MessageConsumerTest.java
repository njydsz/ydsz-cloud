package com.njydsz.pmis.message.consumer;

import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.common.feign.MessageRequest;
import com.njydsz.pmis.message.service.MessageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.HashMap;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * MessageConsumer 单元测试（不依赖 RocketMQ 启动）
 *
 * <p>P0-6: 覆盖 Redis SETNX 幂等防重逻辑
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("MessageConsumer 消息消费幂等测试")
@ExtendWith(MockitoExtension.class)
class MessageConsumerTest {

    @Mock
    private MessageService messageService;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOps;

    @InjectMocks
    private MessageConsumer consumer;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
    }

    // ==================== 正常消费（幂等锁获取成功） ====================

    @Test
    @DisplayName("onMessage 正常消息体 → 获取幂等锁 → 调 service.send")
    void onMessage_normal() {
        MessageRequest req = buildRequest("EMAIL", "TPL-001", "test@ydsz-pmis.cn",
                "OPPORTUNITY", "OPP-001", null);
        String body = toJson(req);

        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(true);

        consumer.onMessage(body);

        verify(messageService, times(1)).send(any(MessageRequest.class));
        // 幂等键应基于 bizType:bizId:templateCode:receiver
        verify(valueOps).setIfAbsent(
                eq("pmis:message:idempotent:OPPORTUNITY:OPP-001:TPL-001:test@ydsz-pmis.cn"),
                anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("onMessage 有 messageId → 使用 messageId 作为幂等键")
    void onMessage_withMessageId() {
        MessageRequest req = buildRequest("EMAIL", "TPL-001", "test@ydsz-pmis.cn",
                "OPPORTUNITY", "OPP-001", "msg-uuid-123");
        String body = toJson(req);

        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(true);

        consumer.onMessage(body);

        verify(messageService, times(1)).send(any(MessageRequest.class));
        verify(valueOps).setIfAbsent(
                eq("pmis:message:idempotent:msg-uuid-123"),
                anyString(), any(Duration.class));
    }

    // ==================== 重复消息（幂等锁获取失败） ====================

    @Test
    @DisplayName("onMessage 重复消息 → 幂等锁获取失败 → 跳过，不调 service")
    void onMessage_duplicate_skip() {
        MessageRequest req = buildRequest("SMS", "TPL-002", "13800138000",
                "CONTRACT", "CON-001", null);
        String body = toJson(req);

        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(false);

        consumer.onMessage(body);

        // 不应调用 service.send
        verify(messageService, never()).send(any());
    }

    // ==================== 异常处理 ====================

    @Test
    @DisplayName("onMessage 业务异常 → 保留锁，不重试，不释放锁")
    void onMessage_bizException_keepLock() {
        MessageRequest req = buildRequest("SMS", "NOT-EXIST", "13800138000",
                "OPPORTUNITY", "OPP-002", null);
        String body = toJson(req);

        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(true);
        org.mockito.Mockito.doThrow(new BizException(400, "模板不存在"))
                .when(messageService).send(any(MessageRequest.class));

        consumer.onMessage(body);

        verify(messageService, times(1)).send(any(MessageRequest.class));
        // BizException 不应释放锁
        verify(redisTemplate, never()).execute(any(RedisScript.class), any(), any());
    }

    @Test
    @DisplayName("onMessage 系统异常 → 释放锁，抛出 RuntimeException 触发重投")
    void onMessage_systemException_releaseAndRethrow() {
        MessageRequest req = buildRequest("EMAIL", "TPL-001", "test@ydsz-pmis.cn",
                "OPPORTUNITY", "OPP-003", null);
        String body = toJson(req);

        when(valueOps.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(true);
        org.mockito.Mockito.doThrow(new RuntimeException("DB down"))
                .when(messageService).send(any(MessageRequest.class));

        assertThatThrownBy(() -> consumer.onMessage(body))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("MessageConsumer failed");

        verify(messageService, times(1)).send(any(MessageRequest.class));
        // 系统异常应释放锁
        verify(redisTemplate, times(1)).execute(any(RedisScript.class), any(), any());
    }

    // ==================== 幂等键缺失场景 ====================

    @Test
    @DisplayName("onMessage 幂等键字段缺失（无 messageId + bizType/bizId/template/receiver 不全）→ 跳过幂等检查，直接消费")
    void onMessage_noIdempotentKey_directConsume() {
        MessageRequest req = new MessageRequest();
        req.setChannel("EMAIL");
        req.setTemplateCode("TPL-001");
        // 缺少 bizType/bizId/receiver，无 messageId
        String body = toJson(req);

        consumer.onMessage(body);

        // 不应调用 Redis
        verify(redisTemplate, never()).opsForValue();
        // 但应直接调用 service.send（降级模式）
        verify(messageService, times(1)).send(any(MessageRequest.class));
    }

    // ==================== 边界场景 ====================

    @Test
    @DisplayName("onMessage 空 body → 跳过，不调 service，不调 Redis")
    void onMessage_empty() {
        consumer.onMessage(null);
        consumer.onMessage("");
        consumer.onMessage("   ");
        verifyNoInteractions(messageService);
        verifyNoInteractions(valueOps);
    }

    @Test
    @DisplayName("onMessage 非法 JSON → 跳过，不调 service，不调 Redis")
    void onMessage_invalidJson() {
        consumer.onMessage("not a json");
        verifyNoInteractions(messageService);
        verifyNoInteractions(valueOps);
    }

    // ==================== 辅助方法 ====================

    private MessageRequest buildRequest(String channel, String templateCode, String receiver,
                                        String bizType, String bizId, String messageId) {
        MessageRequest req = new MessageRequest();
        req.setChannel(channel);
        req.setTemplateCode(templateCode);
        req.setReceiver(receiver);
        req.setParams(new HashMap<>());
        req.setSubject("测试");
        req.setBizType(bizType);
        req.setBizId(bizId);
        req.setMessageId(messageId);
        return req;
    }

    private String toJson(MessageRequest req) {
        return com.alibaba.fastjson2.JSON.toJSONString(req);
    }
}
