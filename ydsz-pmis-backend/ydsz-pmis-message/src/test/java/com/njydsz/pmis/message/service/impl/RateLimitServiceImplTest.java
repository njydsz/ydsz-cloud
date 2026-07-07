package com.njydsz.pmis.message.service.impl;

import com.njydsz.pmis.message.entity.MsgPreferenceDO;
import com.njydsz.pmis.message.service.PreferenceService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * {@link RateLimitServiceImpl} 单元测试。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("RateLimitServiceImpl 限流频率测试")
@ExtendWith(MockitoExtension.class)
class RateLimitServiceImplTest {

    @Mock
    private RedissonClient redissonClient;
    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private PreferenceService preferenceService;
    @Mock
    private RRateLimiter rateLimiter;

    @InjectMocks
    private RateLimitServiceImpl rateLimitService;

    @Test
    @DisplayName("tryAcquire 获取令牌成功返回 true")
    void tryAcquireShouldReturnTrueWhenAcquired() {
        when(redissonClient.getRateLimiter(anyString())).thenReturn(rateLimiter);
        when(rateLimiter.tryAcquire(anyInt())).thenReturn(true);

        assertTrue(rateLimitService.tryAcquire("sms:alert", 1));
    }

    @Test
    @DisplayName("tryAcquire 令牌不足返回 false")
    void tryAcquireShouldReturnFalseWhenNoToken() {
        when(redissonClient.getRateLimiter(anyString())).thenReturn(rateLimiter);
        when(rateLimiter.tryAcquire(anyInt())).thenReturn(false);

        assertFalse(rateLimitService.tryAcquire("sms:alert", 1));
    }

    @Test
    @DisplayName("checkFrequency 无偏好时不限制返回 true")
    void checkFrequencyShouldReturnTrueWhenNoPreference() {
        when(preferenceService.getByUser(anyString(), anyString(), anyString())).thenReturn(null);
        assertTrue(rateLimitService.checkFrequency("u1", "SMS", "ALERT"));
    }

    @Test
    @DisplayName("checkFrequency 通道关闭返回 false")
    void checkFrequencyShouldReturnFalseWhenChannelDisabled() {
        MsgPreferenceDO pref = new MsgPreferenceDO();
        pref.setEnabled(0);
        when(preferenceService.getByUser(anyString(), anyString(), anyString())).thenReturn(pref);
        assertFalse(rateLimitService.checkFrequency("u1", "SMS", "ALERT"));
    }

    @Test
    @DisplayName("checkFrequency 每日超限返回 false")
    @SuppressWarnings("unchecked")
    void checkFrequencyShouldReturnFalseWhenDailyExceeded() {
        MsgPreferenceDO pref = new MsgPreferenceDO();
        pref.setEnabled(1);
        pref.setDailyLimit(10);
        ValueOperations<String, String> ops = org.mockito.Mockito.mock(ValueOperations.class);
        when(preferenceService.getByUser(anyString(), anyString(), anyString())).thenReturn(pref);
        when(stringRedisTemplate.opsForValue()).thenReturn(ops);
        when(ops.get(anyString())).thenReturn("10");

        assertFalse(rateLimitService.checkFrequency("u1", "SMS", "ALERT"));
    }
}
