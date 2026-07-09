package com.njydsz.pmis.message.service.impl;

import com.njydsz.pmis.message.config.MessageProperties;
import com.njydsz.pmis.message.constant.MessageConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 智能去重服务单元测试。
 *
 * <p>覆盖 SET NX EX 原子去重的正常路径、边界条件、Redis 异常降级（fail-open）。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("去重服务 DedupServiceImpl 单元测试")
class DedupServiceImplTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private MessageProperties messageProperties;

    @InjectMocks
    private DedupServiceImpl dedupService;

    @BeforeEach
    void setUp() {
        // 默认配置：开启去重，TTL=60s
        MessageProperties.DedupConfig cfg = new MessageProperties.DedupConfig();
        lenient().when(messageProperties.getDedup()).thenReturn(cfg);
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @DisplayName("正常场景：首次写入成功（SET NX EX 返回 true）应放行")
    void 首次写入成功应放行() {
        String dedupKey = "order:123:SMS:u1";
        when(valueOperations.setIfAbsent(
                eq(MessageConstants.DEDUP_KEY_PREFIX + dedupKey), eq("1"), any(Duration.class)))
                .thenReturn(Boolean.TRUE);

        boolean result = dedupService.tryAcquire(dedupKey);

        assertTrue(result, "首次写入应返回 true");
        verify(valueOperations).setIfAbsent(
                eq(MessageConstants.DEDUP_KEY_PREFIX + dedupKey), eq("1"), any(Duration.class));
    }

    @Test
    @DisplayName("重复场景：窗口内重复写入（返回 false）应拦截")
    void 重复写入应拦截() {
        String dedupKey = "order:123:SMS:u1";
        when(valueOperations.setIfAbsent(
                eq(MessageConstants.DEDUP_KEY_PREFIX + dedupKey), eq("1"), any(Duration.class)))
                .thenReturn(Boolean.FALSE);

        boolean result = dedupService.tryAcquire(dedupKey);

        assertFalse(result, "重复写入应返回 false");
    }

    @Test
    @DisplayName("边界场景：dedupKey 为 null 应直接放行")
    void dedupKey为null应放行() {
        boolean result = dedupService.tryAcquire(null);
        assertTrue(result, "null key 应放行");
    }

    @Test
    @DisplayName("边界场景：dedupKey 为空白应直接放行")
    void dedupKey为空白应放行() {
        boolean result = dedupService.tryAcquire("   ");
        assertTrue(result, "空白 key 应放行");
    }

    @Test
    @DisplayName("边界场景：去重总开关关闭应直接放行")
    void 去重关闭应放行() {
        MessageProperties.DedupConfig cfg = new MessageProperties.DedupConfig();
        cfg.setEnabled(false);
        when(messageProperties.getDedup()).thenReturn(cfg);

        boolean result = dedupService.tryAcquire("any-key");

        assertTrue(result, "去重关闭时应放行");
    }

    @Test
    @DisplayName("边界场景：dedupConfig 为 null 应直接放行")
    void dedupConfig为null应放行() {
        when(messageProperties.getDedup()).thenReturn(null);

        boolean result = dedupService.tryAcquire("any-key");

        assertTrue(result, "配置为 null 时应放行");
    }

    @Test
    @DisplayName("边界场景：ttlSeconds 为 0 时使用默认 60s")
    void ttl为零时使用默认值() {
        MessageProperties.DedupConfig cfg = new MessageProperties.DedupConfig();
        cfg.setTtlSeconds(0);
        when(messageProperties.getDedup()).thenReturn(cfg);
        when(valueOperations.setIfAbsent(any(String.class), eq("1"), any(Duration.class)))
                .thenReturn(Boolean.TRUE);

        boolean result = dedupService.tryAcquire("k");

        assertTrue(result);
        // 默认 ttl=60s，Duration.ofSeconds(60)
        verify(valueOperations).setIfAbsent(any(String.class), eq("1"), eq(Duration.ofSeconds(60)));
    }

    @Test
    @DisplayName("异常场景：Redis 异常时 fail-open 放行")
    void redis异常时failOpen放行() {
        when(valueOperations.setIfAbsent(any(String.class), eq("1"), any(Duration.class)))
                .thenThrow(new RuntimeException("Redis 连接失败"));

        boolean result = dedupService.tryAcquire("any-key");

        assertTrue(result, "Redis 异常时应 fail-open 放行");
    }

    @Test
    @DisplayName("边界场景：setIfAbsent 返回 null 应视为未抢到（拦截）")
    void setIfAbsent返回null应拦截() {
        when(valueOperations.setIfAbsent(any(String.class), eq("1"), any(Duration.class)))
                .thenReturn(null);

        boolean result = dedupService.tryAcquire("any-key");

        assertFalse(result, "null 返回值应视为已存在，拦截");
    }
}
