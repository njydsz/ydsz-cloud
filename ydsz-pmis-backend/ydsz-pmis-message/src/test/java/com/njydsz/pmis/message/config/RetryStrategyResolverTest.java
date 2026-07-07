package com.njydsz.pmis.message.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P1-7: {@link RetryStrategyResolver} 单元测试。
 *
 * <p>验证重试策略解析优先级（通道级 > 全局默认 > 代码兜底）、
 * 退避计算公式（指数退避 + 上限封顶）、最大重试判断。
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@DisplayName("RetryStrategyResolver 重试策略解析测试")
class RetryStrategyResolverTest {

    private MessageProperties properties;
    private RetryStrategyResolver resolver;

    @BeforeEach
    void setUp() {
        properties = new MessageProperties();
        resolver = new RetryStrategyResolver(properties);
    }

    // ========== resolve ==========

    @Test
    @DisplayName("resolve 返回全局默认策略（未配置 channelRetryPolicies）")
    void resolveShouldReturnDefaultWhenNoChannelOverride() {
        MessageProperties.RetryPolicy policy = resolver.resolve("SMS");

        assertNotNull(policy);
        assertEquals(3, policy.getMaxRetryCount());
        assertEquals(2000L, policy.getBaseBackoffMs());
        assertEquals(2.0, policy.getBackoffMultiplier());
        assertEquals(60000L, policy.getMaxBackoffMs());
    }

    @Test
    @DisplayName("resolve 返回通道级覆盖策略")
    void resolveShouldReturnChannelOverride() {
        MessageProperties.RetryPolicy smsPolicy = new MessageProperties.RetryPolicy();
        smsPolicy.setMaxRetryCount(5);
        smsPolicy.setBaseBackoffMs(1000L);
        Map<String, MessageProperties.RetryPolicy> map = new HashMap<>();
        map.put("SMS", smsPolicy);
        properties.setChannelRetryPolicies(map);

        MessageProperties.RetryPolicy policy = resolver.resolve("SMS");

        assertEquals(5, policy.getMaxRetryCount());
        assertEquals(1000L, policy.getBaseBackoffMs());
    }

    @Test
    @DisplayName("resolve 通道名大小写无关")
    void resolveShouldBeCaseInsensitive() {
        MessageProperties.RetryPolicy emailPolicy = new MessageProperties.RetryPolicy();
        emailPolicy.setMaxRetryCount(1);
        Map<String, MessageProperties.RetryPolicy> map = new HashMap<>();
        map.put("EMAIL", emailPolicy);
        properties.setChannelRetryPolicies(map);

        assertEquals(1, resolver.resolve("email").getMaxRetryCount());
        assertEquals(1, resolver.resolve("Email").getMaxRetryCount());
        assertEquals(1, resolver.resolve("EMAIL").getMaxRetryCount());
    }

    @Test
    @DisplayName("resolve 未命中通道回退全局默认")
    void resolveShouldFallbackToDefaultForUnmatchedChannel() {
        MessageProperties.RetryPolicy smsPolicy = new MessageProperties.RetryPolicy();
        smsPolicy.setMaxRetryCount(10);
        Map<String, MessageProperties.RetryPolicy> map = new HashMap<>();
        map.put("SMS", smsPolicy);
        properties.setChannelRetryPolicies(map);

        // EMAIL 未配置,回退默认
        assertEquals(3, resolver.resolve("EMAIL").getMaxRetryCount());
    }

    @Test
    @DisplayName("resolve 空 channel 返回全局默认")
    void resolveShouldReturnDefaultForBlankChannel() {
        assertNotNull(resolver.resolve(null));
        assertNotNull(resolver.resolve(""));
        assertNotNull(resolver.resolve("   "));
        assertEquals(3, resolver.resolve(null).getMaxRetryCount());
    }

    @Test
    @DisplayName("resolve 全局默认为 null 时兜底代码默认值")
    void resolveShouldUseCodeDefaultWhenGlobalNull() {
        properties.setDefaultRetryPolicy(null);

        MessageProperties.RetryPolicy policy = resolver.resolve("SMS");

        assertNotNull(policy);
        assertEquals(3, policy.getMaxRetryCount());
    }

    // ========== isMaxRetriesReached ==========

    @Test
    @DisplayName("isMaxRetriesReached 默认 max=3：0/1/2 未达上限，3 已达")
    void isMaxRetriesReachedDefaultThreshold() {
        assertFalse(resolver.isMaxRetriesReached(0, "SMS"));
        assertFalse(resolver.isMaxRetriesReached(1, "SMS"));
        assertFalse(resolver.isMaxRetriesReached(2, "SMS"));
        assertTrue(resolver.isMaxRetriesReached(3, "SMS"));
        assertTrue(resolver.isMaxRetriesReached(99, "SMS"));
    }

    @Test
    @DisplayName("isMaxRetriesReached 通道级覆盖 max=1")
    void isMaxRetriesReachedWithChannelOverride() {
        MessageProperties.RetryPolicy pushPolicy = new MessageProperties.RetryPolicy();
        pushPolicy.setMaxRetryCount(1);
        Map<String, MessageProperties.RetryPolicy> map = new HashMap<>();
        map.put("PUSH", pushPolicy);
        properties.setChannelRetryPolicies(map);

        assertFalse(resolver.isMaxRetriesReached(0, "PUSH"));
        assertTrue(resolver.isMaxRetriesReached(1, "PUSH"));
    }

    // ========== calcBackoffMs ==========

    @Test
    @DisplayName("calcBackoffMs 默认指数退避：base=2000 multiplier=2.0")
    void calcBackoffMsDefaultExponential() {
        // 2000 * 2^0 = 2000
        assertEquals(2000L, resolver.calcBackoffMs(0, "SMS"));
        // 2000 * 2^1 = 4000
        assertEquals(4000L, resolver.calcBackoffMs(1, "SMS"));
        // 2000 * 2^2 = 8000
        assertEquals(8000L, resolver.calcBackoffMs(2, "SMS"));
        // 2000 * 2^3 = 16000
        assertEquals(16000L, resolver.calcBackoffMs(3, "SMS"));
    }

    @Test
    @DisplayName("calcBackoffMs 受 maxBackoffMs 封顶")
    void calcBackoffMsCappedByMaxBackoffMs() {
        // 默认 max=60000: 2000*2^5=64000 → 60000
        assertEquals(60000L, resolver.calcBackoffMs(5, "SMS"));
        // 更高次数仍为 60000
        assertEquals(60000L, resolver.calcBackoffMs(10, "SMS"));
    }

    @Test
    @DisplayName("calcBackoffMs 自定义倍率与上限")
    void calcBackoffMsCustomMultiplierAndCap() {
        MessageProperties.RetryPolicy policy = new MessageProperties.RetryPolicy();
        policy.setBaseBackoffMs(1000L);
        policy.setBackoffMultiplier(3.0);
        policy.setMaxBackoffMs(5000L);
        Map<String, MessageProperties.RetryPolicy> map = new HashMap<>();
        map.put("EMAIL", policy);
        properties.setChannelRetryPolicies(map);

        // 1000 * 3^0 = 1000
        assertEquals(1000L, resolver.calcBackoffMs(0, "EMAIL"));
        // 1000 * 3^1 = 3000
        assertEquals(3000L, resolver.calcBackoffMs(1, "EMAIL"));
        // 1000 * 3^2 = 9000 → 5000
        assertEquals(5000L, resolver.calcBackoffMs(2, "EMAIL"));
    }

    @Test
    @DisplayName("calcBackoffMs 负数 retryCount 当 0 处理")
    void calcBackoffMsNegativeRetryCountTreatedAsZero() {
        assertEquals(2000L, resolver.calcBackoffMs(-1, "SMS"));
        assertEquals(2000L, resolver.calcBackoffMs(-100, "SMS"));
    }

    // ========== calcNextRetryAt ==========

    @Test
    @DisplayName("calcNextRetryAt 返回未来时间")
    void calcNextRetryAtReturnsFutureTime() {
        LocalDateTime before = LocalDateTime.now();
        LocalDateTime nextRetry = resolver.calcNextRetryAt(0, "SMS");
        LocalDateTime after = LocalDateTime.now().plusSeconds(3);

        assertNotNull(nextRetry);
        assertTrue(nextRetry.isAfter(before));
        // 退避 2000ms = 2s,加判断耗时,应在 3s 内
        assertTrue(nextRetry.isBefore(after) || nextRetry.isEqual(after));
    }
}
