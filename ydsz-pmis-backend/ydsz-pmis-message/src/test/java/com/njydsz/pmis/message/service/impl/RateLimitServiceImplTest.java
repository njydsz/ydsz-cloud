package com.njydsz.pmis.message.service.impl;

import com.njydsz.pmis.message.config.MessageProperties;
import com.njydsz.pmis.message.constant.MessageConstants;
import com.njydsz.pmis.message.entity.config.MsgPreferenceDO;
import com.njydsz.pmis.message.service.config.PreferenceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 限流与频率控制服务单元测试。
 *
 * <p>覆盖 Redisson 令牌桶限流、Redis INCR 频率统计、多维度限流、URGENT 优先级绕过、Redis 异常降级。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("限流服务 RateLimitServiceImpl 单元测试")
class RateLimitServiceImplTest {

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private PreferenceService preferenceService;

    @Mock
    private MessageProperties messageProperties;

    @Mock
    private RRateLimiter rateLimiter;

    @InjectMocks
    private RateLimitServiceImpl rateLimitService;

    @BeforeEach
    void setUp() {
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    // ==================== tryAcquire ====================

    @Test
    @DisplayName("正常场景：令牌桶获取成功应返回 true")
    void 令牌桶获取成功返回True() {
        String key = "SMS:default";
        when(redissonClient.getRateLimiter(MessageConstants.RATE_LIMIT_KEY_PREFIX + key)).thenReturn(rateLimiter);
        when(rateLimiter.tryAcquire(1)).thenReturn(true);

        boolean result = rateLimitService.tryAcquire(key, 1);

        assertTrue(result);
        verify(rateLimiter).trySetRate(eq(RateType.OVERALL), eq(1L), eq(1L), eq(RateIntervalUnit.SECONDS));
        verify(rateLimiter).tryAcquire(1);
    }

    @Test
    @DisplayName("限流场景：令牌桶获取失败应返回 false")
    void 令牌桶获取失败返回False() {
        String key = "SMS:default";
        when(redissonClient.getRateLimiter(MessageConstants.RATE_LIMIT_KEY_PREFIX + key)).thenReturn(rateLimiter);
        when(rateLimiter.tryAcquire(1)).thenReturn(false);

        boolean result = rateLimitService.tryAcquire(key, 1);

        assertFalse(result);
    }

    @Test
    @DisplayName("边界场景：key 为 null 应直接放行")
    void key为null应放行() {
        boolean result = rateLimitService.tryAcquire(null, 1);
        assertTrue(result);
    }

    @Test
    @DisplayName("边界场景：key 为空白应直接放行")
    void key为空白应放行() {
        boolean result = rateLimitService.tryAcquire("  ", 1);
        assertTrue(result);
    }

    @Test
    @DisplayName("边界场景：permits <= 0 应直接放行")
    void permits为零应放行() {
        boolean result = rateLimitService.tryAcquire("any", 0);
        assertTrue(result);
    }

    @Test
    @DisplayName("异常场景：Redisson 异常时降级放行")
    void redisson异常时降级放行() {
        when(redissonClient.getRateLimiter(any(String.class))).thenThrow(new RuntimeException("Redis 不可用"));

        boolean result = rateLimitService.tryAcquire("any", 1);

        assertTrue(result, "Redisson 异常时应降级放行");
    }

    // ==================== checkFrequency ====================

    @Test
    @DisplayName("正常场景：无偏好配置视为不限制")
    void 无偏好配置视为不限制() {
        when(preferenceService.getByUser("u1", "SMS", "default")).thenReturn(null);

        boolean result = rateLimitService.checkFrequency("u1", "SMS", "default");

        assertTrue(result);
    }

    @Test
    @DisplayName("边界场景：userId 为空应直接放行")
    void userId为空应放行() {
        boolean result = rateLimitService.checkFrequency(null, "SMS", "default");
        assertTrue(result);
    }

    @Test
    @DisplayName("限制场景：偏好 enabled=0（关闭通道）应拒绝")
    void 偏好关闭通道应拒绝() {
        MsgPreferenceDO pref = new MsgPreferenceDO();
        pref.setEnabled(0);
        when(preferenceService.getByUser("u1", "SMS", "default")).thenReturn(pref);

        boolean result = rateLimitService.checkFrequency("u1", "SMS", "default");

        assertFalse(result, "通道关闭时不允许发送");
    }

    @Test
    @DisplayName("边界场景：偏好 enabled 为 null 视为不限制")
    void 偏好enabled为null不限制() {
        MsgPreferenceDO pref = new MsgPreferenceDO();
        pref.setEnabled(null);
        when(preferenceService.getByUser("u1", "SMS", "default")).thenReturn(pref);

        boolean result = rateLimitService.checkFrequency("u1", "SMS", "default");

        assertTrue(result);
    }

    @Test
    @DisplayName("限制场景：小时频率超限应拒绝")
    void 小时频率超限应拒绝() {
        MsgPreferenceDO pref = new MsgPreferenceDO();
        pref.setEnabled(1);
        pref.setHourlyLimit(10);
        pref.setDailyLimit(100);
        when(preferenceService.getByUser("u1", "SMS", "default")).thenReturn(pref);
        // 模拟当前小时计数已达上限
        when(valueOperations.get(any(String.class))).thenReturn("10");

        boolean result = rateLimitService.checkFrequency("u1", "SMS", "default");

        assertFalse(result, "小时频率超限应拒绝");
    }

    @Test
    @DisplayName("限制场景：日频率超限应拒绝")
    void 日频率超限应拒绝() {
        MsgPreferenceDO pref = new MsgPreferenceDO();
        pref.setEnabled(1);
        pref.setHourlyLimit(100);
        pref.setDailyLimit(10);
        when(preferenceService.getByUser("u1", "SMS", "default")).thenReturn(pref);
        // 小时不超限返回 0，日超限返回 10
        when(valueOperations.get(any(String.class))).thenReturn("0", "10");

        boolean result = rateLimitService.checkFrequency("u1", "SMS", "default");

        assertFalse(result, "日频率超限应拒绝");
    }

    @Test
    @DisplayName("正常场景：频率未超限应放行")
    void 频率未超限应放行() {
        MsgPreferenceDO pref = new MsgPreferenceDO();
        pref.setEnabled(1);
        pref.setHourlyLimit(100);
        pref.setDailyLimit(1000);
        when(preferenceService.getByUser("u1", "SMS", "default")).thenReturn(pref);
        when(valueOperations.get(any(String.class))).thenReturn("5");

        boolean result = rateLimitService.checkFrequency("u1", "SMS", "default");

        assertTrue(result);
    }

    @Test
    @DisplayName("边界场景：频率计数为非数字时视为 0 放行")
    void 频率计数非数字视为0放行() {
        MsgPreferenceDO pref = new MsgPreferenceDO();
        pref.setEnabled(1);
        pref.setHourlyLimit(10);
        pref.setDailyLimit(100);
        when(preferenceService.getByUser("u1", "SMS", "default")).thenReturn(pref);
        when(valueOperations.get(any(String.class))).thenReturn("not-a-number");

        boolean result = rateLimitService.checkFrequency("u1", "SMS", "default");

        assertTrue(result);
    }

    // ==================== recordFrequency ====================

    @Test
    @DisplayName("正常场景：记录频率计数时 increment + expire")
    void 记录频率计数() {
        when(valueOperations.increment(any(String.class))).thenReturn(1L);

        rateLimitService.recordFrequency("u1", "SMS", "default");

        // hourly 与 daily 两个计数器 increment 均返回 1，各触发一次 expire
        verify(stringRedisTemplate, times(2)).expire(any(String.class), anyLong(), eq(TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("边界场景：userId 为空时不记录频率")
    void userId为空不记录频率() {
        rateLimitService.recordFrequency(null, "SMS", "default");
        verify(valueOperations, never()).increment(any(String.class));
    }

    @Test
    @DisplayName("边界场景：increment 返回非 1 时不设置 expire")
    void increment非1不设置Expire() {
        when(valueOperations.increment(any(String.class))).thenReturn(5L);

        rateLimitService.recordFrequency("u1", "SMS", "default");

        verify(stringRedisTemplate, never()).expire(any(String.class), anyLong(), any(TimeUnit.class));
    }

    @Test
    @DisplayName("异常场景：increment 异常时降级忽略")
    void increment异常时降级忽略() {
        when(valueOperations.increment(any(String.class))).thenThrow(new RuntimeException("Redis 异常"));

        // 不应抛异常
        rateLimitService.recordFrequency("u1", "SMS", "default");
    }

    // ==================== checkSendLimit ====================

    @Test
    @DisplayName("边界场景：rateLimit 配置为 null 视为不限制")
    void rateLimit配置为null不限制() {
        when(messageProperties.getRateLimit()).thenReturn(null);

        boolean result = rateLimitService.checkSendLimit("SMS", "u1", "tpl", "t1");

        assertTrue(result);
    }

    @Test
    @DisplayName("正常场景：多维度限流全部通过")
    void 多维度限流全部通过() {
        MessageProperties.RateLimitConfig cfg = new MessageProperties.RateLimitConfig();
        when(messageProperties.getRateLimit()).thenReturn(cfg);
        // 三个维度的 limiter 都放行
        when(redissonClient.getRateLimiter(any(String.class))).thenReturn(rateLimiter);
        when(rateLimiter.tryAcquire(1)).thenReturn(true);

        boolean result = rateLimitService.checkSendLimit("SMS", "u1", "tpl", "t1");

        assertTrue(result);
    }

    @Test
    @DisplayName("限制场景：receiver 维度限流命中应拒绝")
    void receiver维度限流命中应拒绝() {
        MessageProperties.RateLimitConfig cfg = new MessageProperties.RateLimitConfig();
        when(messageProperties.getRateLimit()).thenReturn(cfg);
        when(redissonClient.getRateLimiter(any(String.class))).thenReturn(rateLimiter);
        when(rateLimiter.tryAcquire(1)).thenReturn(false);

        boolean result = rateLimitService.checkSendLimit("SMS", "u1", "tpl", "t1");

        assertFalse(result);
    }

    @Test
    @DisplayName("边界场景：receiver 为空时跳过 receiver 维度")
    void receiver为空跳过receiver维度() {
        MessageProperties.RateLimitConfig cfg = new MessageProperties.RateLimitConfig();
        when(messageProperties.getRateLimit()).thenReturn(cfg);
        when(redissonClient.getRateLimiter(any(String.class))).thenReturn(rateLimiter);
        when(rateLimiter.tryAcquire(1)).thenReturn(true);

        boolean result = rateLimitService.checkSendLimit("SMS", null, "tpl", "t1");

        assertTrue(result);
    }

    // ==================== checkSendLimit（优先级感知） ====================

    @Test
    @DisplayName("优先级场景：URGENT 跳过 template 和 tenant 维度，仅检查 receiver")
    void urgent跳过template和Tenant维度() {
        MessageProperties.RateLimitConfig cfg = new MessageProperties.RateLimitConfig();
        when(messageProperties.getRateLimit()).thenReturn(cfg);
        when(redissonClient.getRateLimiter(any(String.class))).thenReturn(rateLimiter);
        when(rateLimiter.tryAcquire(anyLong())).thenReturn(true);

        boolean result = rateLimitService.checkSendLimit("SMS", "u1", "tpl", "t1", "URGENT");

        assertTrue(result);
        // URGENT 仅调用一次 tryAcquire（receiver 维度）
        verify(rateLimiter).tryAcquire(anyLong());
    }

    @Test
    @DisplayName("优先级场景：URGENT 且 receiver 维度限流命中应拒绝")
    void urgentReceiver维度限流应拒绝() {
        MessageProperties.RateLimitConfig cfg = new MessageProperties.RateLimitConfig();
        when(messageProperties.getRateLimit()).thenReturn(cfg);
        when(redissonClient.getRateLimiter(any(String.class))).thenReturn(rateLimiter);
        when(rateLimiter.tryAcquire(anyLong())).thenReturn(false);

        boolean result = rateLimitService.checkSendLimit("SMS", "u1", "tpl", "t1", "URGENT");

        assertFalse(result);
    }

    @Test
    @DisplayName("优先级场景：URGENT 且 receiver 为空时应直接放行")
    void urgentReceiver为空应放行() {
        MessageProperties.RateLimitConfig cfg = new MessageProperties.RateLimitConfig();
        when(messageProperties.getRateLimit()).thenReturn(cfg);

        boolean result = rateLimitService.checkSendLimit("SMS", null, "tpl", "t1", "URGENT");

        assertTrue(result);
    }

    @Test
    @DisplayName("优先级场景：URGENT 且 rateLimit 配置为 null 应放行")
    void urgent配置为null应放行() {
        when(messageProperties.getRateLimit()).thenReturn(null);

        boolean result = rateLimitService.checkSendLimit("SMS", "u1", "tpl", "t1", "URGENT");

        assertTrue(result);
    }

    @Test
    @DisplayName("优先级场景：URGENT 且 receiverEnabled 关闭应放行")
    void urgentReceiverEnabled关闭应放行() {
        MessageProperties.RateLimitConfig cfg = new MessageProperties.RateLimitConfig();
        cfg.setReceiverEnabled(false);
        when(messageProperties.getRateLimit()).thenReturn(cfg);

        boolean result = rateLimitService.checkSendLimit("SMS", "u1", "tpl", "t1", "URGENT");

        assertTrue(result);
    }

    @Test
    @DisplayName("优先级场景：NORMAL 优先级走全部维度限流")
    void normal走全部维度限流() {
        MessageProperties.RateLimitConfig cfg = new MessageProperties.RateLimitConfig();
        when(messageProperties.getRateLimit()).thenReturn(cfg);
        when(redissonClient.getRateLimiter(any(String.class))).thenReturn(rateLimiter);
        when(rateLimiter.tryAcquire(anyLong())).thenReturn(true);

        boolean result = rateLimitService.checkSendLimit("SMS", "u1", "tpl", "t1", "NORMAL");

        assertTrue(result);
    }
}
