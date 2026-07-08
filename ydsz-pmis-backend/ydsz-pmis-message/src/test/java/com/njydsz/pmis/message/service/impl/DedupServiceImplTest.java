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
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link DedupServiceImpl} 单元测试（P2-1 智能去重）。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("DedupServiceImpl 智能去重测试")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DedupServiceImplTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOps;

    @Mock
    private MessageProperties messageProperties;

    @InjectMocks
    private DedupServiceImpl dedupService;

    @BeforeEach
    void setUp() {
        when(messageProperties.getDedup()).thenReturn(new MessageProperties.DedupConfig());
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
    }

    @Test
    @DisplayName("首次到达(SET NX 成功) → 返回 true")
    void tryAcquireShouldReturnTrueWhenFirstTime() {
        when(valueOps.setIfAbsent(anyString(), eq("1"), anyLong(), eq(TimeUnit.SECONDS)))
                .thenReturn(true);

        boolean result = dedupService.tryAcquire("WELCOME:b1:TPL:u1");

        assertTrue(result);
        verify(valueOps).setIfAbsent(
                eq(MessageConstants.DEDUP_KEY_PREFIX + "WELCOME:b1:TPL:u1"),
                eq("1"), anyLong(), eq(TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("重复到达(SET NX 失败) → 返回 false")
    void tryAcquireShouldReturnFalseWhenDuplicate() {
        when(valueOps.setIfAbsent(anyString(), anyString(), anyLong(), eq(TimeUnit.SECONDS)))
                .thenReturn(false);

        boolean result = dedupService.tryAcquire("WELCOME:b1:TPL:u1");

        assertFalse(result);
    }

    @Test
    @DisplayName("dedupKey 为 null → 直接返回 true(无去重维度)")
    void tryAcquireShouldReturnTrueWhenKeyNull() {
        boolean result = dedupService.tryAcquire(null);

        assertTrue(result);
        verify(valueOps, never()).setIfAbsent(anyString(), anyString(), anyLong(), any());
    }

    @Test
    @DisplayName("dedupKey 为空白 → 直接返回 true(无去重维度)")
    void tryAcquireShouldReturnTrueWhenKeyBlank() {
        boolean result = dedupService.tryAcquire("   ");

        assertTrue(result);
        verify(valueOps, never()).setIfAbsent(anyString(), anyString(), anyLong(), any());
    }

    @Test
    @DisplayName("去重总开关关闭 → 直接返回 true")
    void tryAcquireShouldReturnTrueWhenDisabled() {
        MessageProperties.DedupConfig cfg = new MessageProperties.DedupConfig();
        cfg.setEnabled(false);
        when(messageProperties.getDedup()).thenReturn(cfg);

        boolean result = dedupService.tryAcquire("some-key");

        assertTrue(result);
        verify(valueOps, never()).setIfAbsent(anyString(), anyString(), anyLong(), any());
    }

    @Test
    @DisplayName("Redis 异常 → fail-open 返回 true")
    void tryAcquireShouldReturnTrueWhenRedisThrows() {
        when(valueOps.setIfAbsent(anyString(), anyString(), anyLong(), eq(TimeUnit.SECONDS)))
                .thenThrow(new RuntimeException("redis connection refused"));

        boolean result = dedupService.tryAcquire("some-key");

        assertTrue(result, "Redis 异常时应 fail-open 放行");
    }

    @Test
    @DisplayName("setIfAbsent 返回 null → 视为失败返回 false")
    void tryAcquireShouldReturnFalseWhenSetIfAbsentReturnsNull() {
        when(valueOps.setIfAbsent(anyString(), anyString(), anyLong(), eq(TimeUnit.SECONDS)))
                .thenReturn(null);

        boolean result = dedupService.tryAcquire("some-key");

        assertFalse(result, "setIfAbsent 返回 null 应视为 key 已存在");
    }

    @Test
    @DisplayName("ttlSeconds 配置为 0 或负数 → 回退默认 60s")
    void tryAcquireShouldFallbackToDefaultTtlWhenConfigInvalid() {
        MessageProperties.DedupConfig cfg = new MessageProperties.DedupConfig();
        cfg.setTtlSeconds(0);
        when(messageProperties.getDedup()).thenReturn(cfg);
        when(valueOps.setIfAbsent(anyString(), anyString(), eq(60L), eq(TimeUnit.SECONDS)))
                .thenReturn(true);

        boolean result = dedupService.tryAcquire("some-key");

        assertTrue(result);
        verify(valueOps).setIfAbsent(anyString(), eq("1"), eq(60L), eq(TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("DedupConfig 为 null → 直接返回 true(防御性)")
    void tryAcquireShouldReturnTrueWhenConfigNull() {
        when(messageProperties.getDedup()).thenReturn(null);

        boolean result = dedupService.tryAcquire("some-key");

        assertTrue(result);
        verify(valueOps, never()).setIfAbsent(anyString(), anyString(), anyLong(), any());
    }
}
