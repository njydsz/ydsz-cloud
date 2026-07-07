package com.njydsz.pmis.message.service.impl;

import com.njydsz.pmis.message.config.MessageProperties;
import com.njydsz.pmis.message.entity.MsgPreferenceDO;
import com.njydsz.pmis.message.service.PreferenceService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link RateLimitServiceImpl} 单元测试。
 *
 * <p>P2-5: 新增 {@link #checkSendLimit} 多维度限流测试用例。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("RateLimitServiceImpl 限流频率测试")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RateLimitServiceImplTest {

    @Mock
    private RedissonClient redissonClient;
    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private PreferenceService preferenceService;
    @Mock
    private MessageProperties messageProperties;
    @Mock
    private RRateLimiter rateLimiter;

    @InjectMocks
    private RateLimitServiceImpl rateLimitService;

    @Test
    @DisplayName("tryAcquire 获取令牌成功返回 true")
    void tryAcquireShouldReturnTrueWhenAcquired() {
        when(redissonClient.getRateLimiter(anyString())).thenReturn(rateLimiter);
        when(rateLimiter.tryAcquire(anyLong())).thenReturn(true);

        assertTrue(rateLimitService.tryAcquire("sms:alert", 1));
    }

    @Test
    @DisplayName("tryAcquire 令牌不足返回 false")
    void tryAcquireShouldReturnFalseWhenNoToken() {
        when(redissonClient.getRateLimiter(anyString())).thenReturn(rateLimiter);
        when(rateLimiter.tryAcquire(anyLong())).thenReturn(false);

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
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(preferenceService.getByUser(anyString(), anyString(), anyString())).thenReturn(pref);
        when(stringRedisTemplate.opsForValue()).thenReturn(ops);
        when(ops.get(anyString())).thenReturn("10");

        assertFalse(rateLimitService.checkFrequency("u1", "SMS", "ALERT"));
    }

    // ============ P2-5: checkSendLimit 多维度限流测试 ============

    @Test
    @DisplayName("checkSendLimit: 无配置时返回 true(不限制)")
    void checkSendLimitShouldReturnTrueWhenNoConfig() {
        when(messageProperties.getRateLimit()).thenReturn(null);
        assertTrue(rateLimitService.checkSendLimit("SMS", "u1", "TPL_001", "1"));
    }

    @Test
    @DisplayName("checkSendLimit: 所有维度都通过返回 true")
    void checkSendLimitShouldReturnTrueWhenAllDimensionsPass() {
        MessageProperties.RateLimitConfig cfg = new MessageProperties.RateLimitConfig();
        when(messageProperties.getRateLimit()).thenReturn(cfg);
        when(redissonClient.getRateLimiter(anyString())).thenReturn(rateLimiter);
        when(rateLimiter.tryAcquire(anyLong())).thenReturn(true);

        assertTrue(rateLimitService.checkSendLimit("SMS", "u1", "TPL_001", "1"));
    }

    @Test
    @DisplayName("checkSendLimit: receiver 维度超限返回 false")
    void checkSendLimitShouldReturnFalseWhenReceiverLimited() {
        MessageProperties.RateLimitConfig cfg = new MessageProperties.RateLimitConfig();
        when(messageProperties.getRateLimit()).thenReturn(cfg);
        // receiver 维度 key 前缀
        when(redissonClient.getRateLimiter(startsWith("pmis:msg:ratelimit:receiver:")))
                .thenReturn(rateLimiter);
        when(rateLimiter.tryAcquire(anyLong())).thenReturn(false);

        assertFalse(rateLimitService.checkSendLimit("SMS", "u1", "TPL_001", "1"));
    }

    @Test
    @DisplayName("checkSendLimit: template 维度超限返回 false")
    void checkSendLimitShouldReturnFalseWhenTemplateLimited() {
        MessageProperties.RateLimitConfig cfg = new MessageProperties.RateLimitConfig();
        when(messageProperties.getRateLimit()).thenReturn(cfg);
        // receiver 通过, template 失败
        when(redissonClient.getRateLimiter(startsWith("pmis:msg:ratelimit:receiver:")))
                .thenReturn(rateLimiter);
        when(redissonClient.getRateLimiter(startsWith("pmis:msg:ratelimit:template:")))
                .thenReturn(rateLimiter);
        when(rateLimiter.tryAcquire(anyLong())).thenReturn(true, false);

        assertFalse(rateLimitService.checkSendLimit("SMS", "u1", "TPL_001", "1"));
    }

    @Test
    @DisplayName("checkSendLimit: tenant 维度超限返回 false")
    void checkSendLimitShouldReturnFalseWhenTenantLimited() {
        MessageProperties.RateLimitConfig cfg = new MessageProperties.RateLimitConfig();
        when(messageProperties.getRateLimit()).thenReturn(cfg);
        // receiver 通过, template 通过, tenant 失败
        when(redissonClient.getRateLimiter(anyString())).thenReturn(rateLimiter);
        when(rateLimiter.tryAcquire(anyLong())).thenReturn(true, true, false);

        assertFalse(rateLimitService.checkSendLimit("SMS", "u1", "TPL_001", "1"));
    }

    @Test
    @DisplayName("checkSendLimit: 所有维度禁用时返回 true")
    void checkSendLimitShouldReturnTrueWhenAllDisabled() {
        MessageProperties.RateLimitConfig cfg = new MessageProperties.RateLimitConfig();
        cfg.setReceiverEnabled(false);
        cfg.setTemplateEnabled(false);
        cfg.setTenantEnabled(false);
        when(messageProperties.getRateLimit()).thenReturn(cfg);

        assertTrue(rateLimitService.checkSendLimit("SMS", "u1", "TPL_001", "1"));
    }

    @Test
    @DisplayName("checkSendLimit: receiver 为空时跳过 receiver 维度")
    void checkSendLimitShouldSkipReceiverWhenNull() {
        MessageProperties.RateLimitConfig cfg = new MessageProperties.RateLimitConfig();
        when(messageProperties.getRateLimit()).thenReturn(cfg);
        when(redissonClient.getRateLimiter(anyString())).thenReturn(rateLimiter);
        when(rateLimiter.tryAcquire(anyLong())).thenReturn(true);

        // receiver=null, 只校验 template 和 tenant
        assertTrue(rateLimitService.checkSendLimit("SMS", null, "TPL_001", "1"));
    }
}
