package com.njydsz.pmis.literule.cache;

import com.alibaba.fastjson2.JSON;
import com.github.benmanes.caffeine.cache.Ticker;
import com.njydsz.pmis.literule.api.RuleDefinition;
import com.njydsz.pmis.literule.config.LiteRuleProperties;
import com.njydsz.pmis.literule.event.RuleConfigRefreshEvent;
import com.njydsz.pmis.literule.spi.RuleConfigProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CachingRuleConfigProvider 单元测试（P1-1）
 *
 * <p>测试 Caffeine（L1）+ Redis（L2）两级缓存的核心能力：
 * <ul>
 *   <li>L1 命中/失效/TTL 过期</li>
 *   <li>L2 命中/降级</li>
 *   <li>写操作后版本号递增</li>
 *   <li>监听 RuleConfigRefreshEvent 后 L1 清除</li>
 *   <li>findByCode / loadEnabledRulesByTenant 缓存命中</li>
 *   <li>并发加载安全性（缓存击穿防护）</li>
 * </ul>
 *
 * <p>测试风格参考 {@link com.njydsz.pmis.literule.core.DefaultRuleEngineTest}：
 * Mockito.mock 手动创建，不使用 @ExtendWith。
 *
 * @author ydsz-pmis-team
 */
@DisplayName("CachingRuleConfigProvider 多级缓存单元测试")
class CachingRuleConfigProviderTest {

    private RuleConfigProvider delegate;
    private LiteRuleProperties.CacheConfig cacheConfig;
    private RuleDefinition rule;
    private List<RuleDefinition> rules;

    @BeforeEach
    void setUp() {
        delegate = Mockito.mock(RuleConfigProvider.class);
        cacheConfig = new LiteRuleProperties.CacheConfig();
        // 测试用：L1 TTL 60s，L2 TTL 300s
        cacheConfig.setL1TtlSeconds(60);
        cacheConfig.setL1MaxSize(1000);
        cacheConfig.setL2TtlSeconds(300);
        cacheConfig.setL2Enabled(true);

        rule = RuleDefinition.builder()
                .code("R1")
                .name("规则1")
                .tenantId("1")
                .build();
        rules = List.of(rule);
    }

    // ==================== 辅助方法 ====================

    /**
     * 构造仅 L1 的 Provider（RedissonClient=null，禁用 L2）
     */
    private CachingRuleConfigProvider l1OnlyProvider() {
        return new CachingRuleConfigProvider(delegate, null, cacheConfig, new FakeTicker());
    }

    /**
     * 构造 L1+L2 的 Provider（带 mock RedissonClient）
     */
    private CachingRuleConfigProvider l1L2Provider(RedissonClient redissonClient) {
        return new CachingRuleConfigProvider(delegate, redissonClient, cacheConfig, new FakeTicker());
    }

    /**
     * 构造 L1+L2 的 Provider，并 mock 版本号检查返回 0L
     */
    private CachingRuleConfigProvider l1L2ProviderWithVersionMock(RedissonClient redissonClient,
                                                                   RAtomicLong versionAtomic) {
        when(redissonClient.getAtomicLong("literule:rules:version")).thenReturn(versionAtomic);
        when(versionAtomic.get()).thenReturn(0L);
        return l1L2Provider(redissonClient);
    }

    /**
     * 可控时钟，用于模拟 TTL 过期
     */
    private static class FakeTicker implements Ticker {
        private final AtomicLong nanos = new AtomicLong();

        @Override
        public long read() {
            return nanos.get();
        }

        public void advance(Duration duration) {
            nanos.addAndGet(duration.toNanos());
        }
    }

    // ==================== L1 缓存测试 ====================

    @Nested
    @DisplayName("L1 缓存命中")
    class L1HitTest {

        @Test
        @DisplayName("L1 命中：第二次调用 loadEnabledRules 不访问 delegate")
        void shouldHitL1OnSecondCall() {
            CachingRuleConfigProvider provider = l1OnlyProvider();
            when(delegate.loadEnabledRules()).thenReturn(rules);

            List<RuleDefinition> result1 = provider.loadEnabledRules();
            List<RuleDefinition> result2 = provider.loadEnabledRules();

            assertThat(result1).isEqualTo(rules);
            assertThat(result2).isEqualTo(rules);
            verify(delegate, times(1)).loadEnabledRules();
        }

        @Test
        @DisplayName("L1 命中：findByCode 缓存命中")
        void shouldCacheFindByCode() {
            CachingRuleConfigProvider provider = l1OnlyProvider();
            when(delegate.findByCode("R1")).thenReturn(rule);

            RuleDefinition result1 = provider.findByCode("R1");
            RuleDefinition result2 = provider.findByCode("R1");

            assertThat(result1).isEqualTo(rule);
            assertThat(result2).isEqualTo(rule);
            verify(delegate, times(1)).findByCode("R1");
        }

        @Test
        @DisplayName("L1 命中：loadEnabledRulesByTenant 缓存命中")
        void shouldCacheLoadEnabledRulesByTenant() {
            CachingRuleConfigProvider provider = l1OnlyProvider();
            when(delegate.loadEnabledRulesByTenant("t1")).thenReturn(rules);

            List<RuleDefinition> result1 = provider.loadEnabledRulesByTenant("t1");
            List<RuleDefinition> result2 = provider.loadEnabledRulesByTenant("t1");

            assertThat(result1).isEqualTo(rules);
            assertThat(result2).isEqualTo(rules);
            verify(delegate, times(1)).loadEnabledRulesByTenant("t1");
        }

        @Test
        @DisplayName("L1 命中：findByCode 返回 null 也被缓存（防止缓存穿透）")
        void shouldCacheNullResultForFindByCode() {
            CachingRuleConfigProvider provider = l1OnlyProvider();
            when(delegate.findByCode("NOT_EXIST")).thenReturn(null);

            RuleDefinition result1 = provider.findByCode("NOT_EXIST");
            RuleDefinition result2 = provider.findByCode("NOT_EXIST");

            assertThat(result1).isNull();
            assertThat(result2).isNull();
            verify(delegate, times(1)).findByCode("NOT_EXIST");
        }

        @Test
        @DisplayName("L1 命中：loadAllRules 缓存命中")
        void shouldCacheLoadAllRules() {
            CachingRuleConfigProvider provider = l1OnlyProvider();
            when(delegate.loadAllRules()).thenReturn(rules);

            List<RuleDefinition> result1 = provider.loadAllRules();
            List<RuleDefinition> result2 = provider.loadAllRules();

            assertThat(result1).isEqualTo(rules);
            assertThat(result2).isEqualTo(rules);
            verify(delegate, times(1)).loadAllRules();
        }
    }

    // ==================== L1 失效测试 ====================

    @Nested
    @DisplayName("L1 缓存失效")
    class L1InvalidationTest {

        @Test
        @DisplayName("L1 失效：save 后 L1 清除，下次调用访问 delegate")
        void shouldInvalidateL1OnSave() {
            CachingRuleConfigProvider provider = l1OnlyProvider();
            when(delegate.loadEnabledRules()).thenReturn(rules);
            when(delegate.save(any(), anyString())).thenReturn(rule);

            provider.loadEnabledRules();
            verify(delegate, times(1)).loadEnabledRules();

            provider.save(rule, "admin");

            provider.loadEnabledRules();
            verify(delegate, times(2)).loadEnabledRules();
        }

        @Test
        @DisplayName("L1 失效：toggleEnabled 后 L1 清除")
        void shouldInvalidateL1OnToggleEnabled() {
            CachingRuleConfigProvider provider = l1OnlyProvider();
            when(delegate.loadEnabledRules()).thenReturn(rules);

            provider.loadEnabledRules();
            verify(delegate, times(1)).loadEnabledRules();

            Mockito.doNothing().when(delegate).toggleEnabled(anyString(), anyBoolean(), anyString());
            provider.toggleEnabled("R1", false, "admin");

            provider.loadEnabledRules();
            verify(delegate, times(2)).loadEnabledRules();
        }

        @Test
        @DisplayName("L1 失效：监听 RuleConfigRefreshEvent 后 L1 清除")
        void shouldClearL1OnConfigRefreshEvent() {
            CachingRuleConfigProvider provider = l1OnlyProvider();
            when(delegate.loadEnabledRules()).thenReturn(rules);

            provider.loadEnabledRules();
            verify(delegate, times(1)).loadEnabledRules();

            RuleConfigRefreshEvent event = RuleConfigRefreshEvent.fullReload("admin");
            provider.onConfigRefresh(event);

            provider.loadEnabledRules();
            verify(delegate, times(2)).loadEnabledRules();
        }

        @Test
        @DisplayName("L1 失效：save 异常后仍清除 L1（finally 块）")
        void shouldInvalidateL1EvenIfSaveThrows() {
            CachingRuleConfigProvider provider = l1OnlyProvider();
            when(delegate.loadEnabledRules()).thenReturn(rules);
            when(delegate.save(any(), anyString())).thenThrow(new RuntimeException("DB 异常"));

            provider.loadEnabledRules();
            verify(delegate, times(1)).loadEnabledRules();

            try {
                provider.save(rule, "admin");
            } catch (Exception ignored) {
                // 预期异常
            }

            provider.loadEnabledRules();
            verify(delegate, times(2)).loadEnabledRules();
        }
    }

    // ==================== L1 TTL 过期测试 ====================

    @Nested
    @DisplayName("L1 TTL 过期")
    class L1TtlExpirationTest {

        @Test
        @DisplayName("L1 TTL 过期后重新加载")
        void shouldReloadAfterL1TtlExpires() {
            FakeTicker ticker = new FakeTicker();
            CachingRuleConfigProvider provider = new CachingRuleConfigProvider(
                    delegate, null, cacheConfig, ticker);
            when(delegate.loadEnabledRules()).thenReturn(rules);

            provider.loadEnabledRules();
            verify(delegate, times(1)).loadEnabledRules();

            // 推进时间超过 L1 TTL（60s）
            ticker.advance(Duration.ofSeconds(61));

            provider.loadEnabledRules();
            verify(delegate, times(2)).loadEnabledRules();
        }

        @Test
        @DisplayName("L1 TTL 未过期时不重新加载")
        void shouldNotReloadBeforeL1TtlExpires() {
            FakeTicker ticker = new FakeTicker();
            CachingRuleConfigProvider provider = new CachingRuleConfigProvider(
                    delegate, null, cacheConfig, ticker);
            when(delegate.loadEnabledRules()).thenReturn(rules);

            provider.loadEnabledRules();
            verify(delegate, times(1)).loadEnabledRules();

            // 推进时间但未超过 L1 TTL
            ticker.advance(Duration.ofSeconds(30));

            provider.loadEnabledRules();
            verify(delegate, times(1)).loadEnabledRules();
        }

        @Test
        @DisplayName("findByCode TTL 过期后重新加载")
        void shouldReloadFindByCodeAfterTtlExpires() {
            FakeTicker ticker = new FakeTicker();
            CachingRuleConfigProvider provider = new CachingRuleConfigProvider(
                    delegate, null, cacheConfig, ticker);
            when(delegate.findByCode("R1")).thenReturn(rule);

            provider.findByCode("R1");
            verify(delegate, times(1)).findByCode("R1");

            ticker.advance(Duration.ofSeconds(61));

            provider.findByCode("R1");
            verify(delegate, times(2)).findByCode("R1");
        }
    }

    // ==================== L2 缓存测试 ====================

    @Nested
    @DisplayName("L2 缓存命中")
    class L2HitTest {

        @Test
        @DisplayName("L2 命中：L1 未命中时从 L2 加载")
        void shouldLoadFromL2WhenL1Misses() {
            RedissonClient redissonClient = Mockito.mock(RedissonClient.class);
            RAtomicLong versionAtomic = Mockito.mock(RAtomicLong.class);
            RBucket<String> bucket = Mockito.mock(RBucket.class);

            when(redissonClient.getAtomicLong("literule:rules:version")).thenReturn(versionAtomic);
            when(versionAtomic.get()).thenReturn(0L);
            Mockito.doReturn(bucket).when(redissonClient).getBucket("literule:rules:enabled");
            when(bucket.get()).thenReturn(JSON.toJSONString(rules));

            CachingRuleConfigProvider provider = l1L2Provider(redissonClient);

            // 第一次调用：L1 未命中 -> L2 命中 -> 回填 L1
            List<RuleDefinition> result1 = provider.loadEnabledRules();
            assertThat(result1).isEqualTo(rules);
            verify(delegate, never()).loadEnabledRules();

            // 第二次调用：L1 命中（已回填）
            List<RuleDefinition> result2 = provider.loadEnabledRules();
            assertThat(result2).isEqualTo(rules);
            verify(delegate, never()).loadEnabledRules();
        }

        @Test
        @DisplayName("L2 命中：findByCode 从 L2 加载")
        void shouldLoadFindByCodeFromL2() {
            RedissonClient redissonClient = Mockito.mock(RedissonClient.class);
            RAtomicLong versionAtomic = Mockito.mock(RAtomicLong.class);
            RBucket<String> bucket = Mockito.mock(RBucket.class);

            when(redissonClient.getAtomicLong("literule:rules:version")).thenReturn(versionAtomic);
            when(versionAtomic.get()).thenReturn(0L);
            Mockito.doReturn(bucket).when(redissonClient).getBucket("literule:rules:code:R1");
            when(bucket.get()).thenReturn(JSON.toJSONString(rule));

            CachingRuleConfigProvider provider = l1L2Provider(redissonClient);

            RuleDefinition result = provider.findByCode("R1");
            assertThat(result).isEqualTo(rule);
            verify(delegate, never()).findByCode("R1");
        }

        @Test
        @DisplayName("L2 命中：null 标记从 L2 加载")
        void shouldLoadNullMarkerFromL2() {
            RedissonClient redissonClient = Mockito.mock(RedissonClient.class);
            RAtomicLong versionAtomic = Mockito.mock(RAtomicLong.class);
            RBucket<String> bucket = Mockito.mock(RBucket.class);

            when(redissonClient.getAtomicLong("literule:rules:version")).thenReturn(versionAtomic);
            when(versionAtomic.get()).thenReturn(0L);
            Mockito.doReturn(bucket).when(redissonClient).getBucket("literule:rules:code:NOT_EXIST");
            when(bucket.get()).thenReturn("__NULL__");

            CachingRuleConfigProvider provider = l1L2Provider(redissonClient);

            RuleDefinition result = provider.findByCode("NOT_EXIST");
            assertThat(result).isNull();
            verify(delegate, never()).findByCode("NOT_EXIST");
        }

        @Test
        @DisplayName("L2 未命中：从 DB 加载并回填 L2")
        void shouldLoadFromDbAndFillL2WhenL2Misses() {
            RedissonClient redissonClient = Mockito.mock(RedissonClient.class);
            RAtomicLong versionAtomic = Mockito.mock(RAtomicLong.class);
            RBucket<String> bucket = Mockito.mock(RBucket.class);

            when(redissonClient.getAtomicLong("literule:rules:version")).thenReturn(versionAtomic);
            when(versionAtomic.get()).thenReturn(0L);
            Mockito.doReturn(bucket).when(redissonClient).getBucket("literule:rules:enabled");
            when(bucket.get()).thenReturn(null); // L2 未命中
            when(delegate.loadEnabledRules()).thenReturn(rules);

            CachingRuleConfigProvider provider = l1L2Provider(redissonClient);

            List<RuleDefinition> result = provider.loadEnabledRules();
            assertThat(result).isEqualTo(rules);
            verify(delegate, times(1)).loadEnabledRules();

            // 验证 L2 被回填
            verify(bucket).set(eq(JSON.toJSONString(rules)), eq(300L), eq(TimeUnit.SECONDS));
        }
    }

    // ==================== L2 降级测试 ====================

    @Nested
    @DisplayName("L2 降级")
    class L2DegradationTest {

        @Test
        @DisplayName("L2 不可用降级：RedissonClient 为 null 时仅用 L1")
        void shouldFallbackToL1WhenRedissonClientNull() {
            CachingRuleConfigProvider provider = l1OnlyProvider();
            when(delegate.loadEnabledRules()).thenReturn(rules);

            List<RuleDefinition> result1 = provider.loadEnabledRules();
            List<RuleDefinition> result2 = provider.loadEnabledRules();

            assertThat(result1).isEqualTo(rules);
            assertThat(result2).isEqualTo(rules);
            verify(delegate, times(1)).loadEnabledRules();
        }

        @Test
        @DisplayName("L2 配置禁用：l2Enabled=false 时仅用 L1")
        void shouldDisableL2WhenL2EnabledFalse() {
            cacheConfig.setL2Enabled(false);
            RedissonClient redissonClient = Mockito.mock(RedissonClient.class);
            CachingRuleConfigProvider provider = l1L2Provider(redissonClient);
            when(delegate.loadEnabledRules()).thenReturn(rules);

            List<RuleDefinition> result1 = provider.loadEnabledRules();
            List<RuleDefinition> result2 = provider.loadEnabledRules();

            assertThat(result1).isEqualTo(rules);
            assertThat(result2).isEqualTo(rules);
            verify(delegate, times(1)).loadEnabledRules();
            // L2 被禁用，RedissonClient 不应被调用
            verify(redissonClient, never()).getBucket(anyString());
        }

        @Test
        @DisplayName("L2 读取异常时降级为 DB")
        void shouldFallbackToDbWhenL2ReadFails() {
            RedissonClient redissonClient = Mockito.mock(RedissonClient.class);
            RAtomicLong versionAtomic = Mockito.mock(RAtomicLong.class);
            RBucket<String> bucket = Mockito.mock(RBucket.class);

            when(redissonClient.getAtomicLong("literule:rules:version")).thenReturn(versionAtomic);
            when(versionAtomic.get()).thenReturn(0L);
            Mockito.doReturn(bucket).when(redissonClient).getBucket("literule:rules:enabled");
            when(bucket.get()).thenThrow(new RuntimeException("Redis 不可用"));
            when(delegate.loadEnabledRules()).thenReturn(rules);

            CachingRuleConfigProvider provider = l1L2Provider(redissonClient);

            List<RuleDefinition> result = provider.loadEnabledRules();
            assertThat(result).isEqualTo(rules);
            verify(delegate, times(1)).loadEnabledRules();
        }

        @Test
        @DisplayName("L2 版本号检查异常时不影响 L1 命中")
        void shouldNotBreakWhenVersionCheckFails() {
            RedissonClient redissonClient = Mockito.mock(RedissonClient.class);
            RAtomicLong versionAtomic = Mockito.mock(RAtomicLong.class);

            when(redissonClient.getAtomicLong("literule:rules:version")).thenReturn(versionAtomic);
            when(versionAtomic.get()).thenThrow(new RuntimeException("Redis 版本号读取失败"));
            when(delegate.loadEnabledRules()).thenReturn(rules);

            CachingRuleConfigProvider provider = l1L2Provider(redissonClient);

            List<RuleDefinition> result1 = provider.loadEnabledRules();
            List<RuleDefinition> result2 = provider.loadEnabledRules();

            assertThat(result1).isEqualTo(rules);
            assertThat(result2).isEqualTo(rules);
            verify(delegate, times(1)).loadEnabledRules();
        }
    }

    // ==================== 版本号递增测试 ====================

    @Nested
    @DisplayName("版本号递增")
    class VersionIncrementTest {

        @Test
        @DisplayName("写操作后版本号递增")
        void shouldIncrementVersionOnSave() {
            RedissonClient redissonClient = Mockito.mock(RedissonClient.class);
            RAtomicLong versionAtomic = Mockito.mock(RAtomicLong.class);

            when(redissonClient.getAtomicLong("literule:rules:version")).thenReturn(versionAtomic);
            when(versionAtomic.get()).thenReturn(0L);
            when(versionAtomic.incrementAndGet()).thenReturn(1L);
            when(delegate.save(any(), anyString())).thenReturn(rule);

            CachingRuleConfigProvider provider = l1L2Provider(redissonClient);
            provider.save(rule, "admin");

            verify(versionAtomic).incrementAndGet();
        }

        @Test
        @DisplayName("toggleEnabled 后版本号递增")
        void shouldIncrementVersionOnToggleEnabled() {
            RedissonClient redissonClient = Mockito.mock(RedissonClient.class);
            RAtomicLong versionAtomic = Mockito.mock(RAtomicLong.class);

            when(redissonClient.getAtomicLong("literule:rules:version")).thenReturn(versionAtomic);
            when(versionAtomic.get()).thenReturn(0L);
            when(versionAtomic.incrementAndGet()).thenReturn(1L);
            Mockito.doNothing().when(delegate).toggleEnabled(anyString(), anyBoolean(), anyString());

            CachingRuleConfigProvider provider = l1L2Provider(redissonClient);
            provider.toggleEnabled("R1", false, "admin");

            verify(versionAtomic).incrementAndGet();
        }

        @Test
        @DisplayName("L1 only 模式下写操作不调用版本号递增")
        void shouldNotIncrementVersionWhenL2Disabled() {
            CachingRuleConfigProvider provider = l1OnlyProvider();
            when(delegate.save(any(), anyString())).thenReturn(rule);

            provider.save(rule, "admin");

            // 无 RedissonClient，无法递增版本号；仅清 L1
            // 无异常即通过
        }
    }

    // ==================== 并发加载安全性测试 ====================

    @Nested
    @DisplayName("并发加载安全性")
    class ConcurrencyTest {

        @Test
        @DisplayName("并发加载：多线程同时调用仅触发一次 delegate")
        void shouldLoadOnlyOnceConcurrently() throws Exception {
            CachingRuleConfigProvider provider = l1OnlyProvider();

            // 模拟 DB 慢查询，确保多线程同时到达
            when(delegate.loadEnabledRules()).thenAnswer(invocation -> {
                Thread.sleep(100);
                return rules;
            });

            int threadCount = 10;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            List<Callable<List<RuleDefinition>>> tasks = new ArrayList<>();
            for (int i = 0; i < threadCount; i++) {
                tasks.add(() -> {
                    startLatch.await();
                    return provider.loadEnabledRules();
                });
            }

            List<Future<List<RuleDefinition>>> futures = executor.invokeAll(tasks);
            startLatch.countDown();

            for (Future<List<RuleDefinition>> future : futures) {
                assertThat(future.get()).isEqualTo(rules);
            }
            verify(delegate, times(1)).loadEnabledRules();
            executor.shutdown();
        }

        @Test
        @DisplayName("并发加载：findByCode 多线程同时调用仅触发一次 delegate")
        void shouldLoadOnlyOnceConcurrentlyForFindByCode() throws Exception {
            CachingRuleConfigProvider provider = l1OnlyProvider();

            when(delegate.findByCode("R1")).thenAnswer(invocation -> {
                Thread.sleep(100);
                return rule;
            });

            int threadCount = 8;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            List<Callable<RuleDefinition>> tasks = new ArrayList<>();
            for (int i = 0; i < threadCount; i++) {
                tasks.add(() -> {
                    startLatch.await();
                    return provider.findByCode("R1");
                });
            }

            List<Future<RuleDefinition>> futures = executor.invokeAll(tasks);
            startLatch.countDown();

            for (Future<RuleDefinition> future : futures) {
                assertThat(future.get()).isEqualTo(rule);
            }
            verify(delegate, times(1)).findByCode("R1");
            executor.shutdown();
        }
    }

    // ==================== 多租户隔离测试 ====================

    @Nested
    @DisplayName("多租户缓存隔离")
    class TenantIsolationTest {

        @Test
        @DisplayName("不同租户的 loadEnabledRulesByTenant 独立缓存")
        void shouldCacheDifferentTenantsSeparately() {
            CachingRuleConfigProvider provider = l1OnlyProvider();

            List<RuleDefinition> tenant1Rules = List.of(
                    RuleDefinition.builder().code("T1_R1").tenantId("t1").build());
            List<RuleDefinition> tenant2Rules = List.of(
                    RuleDefinition.builder().code("T2_R1").tenantId("t2").build());

            when(delegate.loadEnabledRulesByTenant("t1")).thenReturn(tenant1Rules);
            when(delegate.loadEnabledRulesByTenant("t2")).thenReturn(tenant2Rules);

            List<RuleDefinition> r1 = provider.loadEnabledRulesByTenant("t1");
            List<RuleDefinition> r2 = provider.loadEnabledRulesByTenant("t2");

            assertThat(r1).isEqualTo(tenant1Rules);
            assertThat(r2).isEqualTo(tenant2Rules);

            // 再次调用应命中缓存
            provider.loadEnabledRulesByTenant("t1");
            provider.loadEnabledRulesByTenant("t2");

            verify(delegate, times(1)).loadEnabledRulesByTenant("t1");
            verify(delegate, times(1)).loadEnabledRulesByTenant("t2");
        }
    }
}
