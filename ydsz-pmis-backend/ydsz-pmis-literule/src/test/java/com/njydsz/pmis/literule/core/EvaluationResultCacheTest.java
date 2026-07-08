package com.njydsz.pmis.literule.core;

import com.njydsz.pmis.literule.api.RuleContext;
import com.njydsz.pmis.literule.api.RuleResult;
import com.njydsz.pmis.literule.api.RuleSeverity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * EvaluationResultCache 单元测试（P2-3 高性能优化）
 *
 * @author ydsz-pmis-team
 */
@DisplayName("EvaluationResultCache 单元测试")
class EvaluationResultCacheTest {

    private EvaluationResultCache cache;

    @BeforeEach
    void setUp() {
        cache = new EvaluationResultCache(60_000L, 100);
    }

    @Nested
    @DisplayName("缓存命中与未命中")
    class HitMissTest {

        @Test
        @DisplayName("相同上下文 - 缓存命中")
        void shouldHitOnSameContext() {
            Map<String, Object> facts = new HashMap<>();
            facts.put("amount", 1000);
            RuleContext context = RuleContext.of(facts, "TEST", "UNIT_TEST");

            List<RuleResult> results = Arrays.asList(
                    RuleResult.triggered("R001", "规则1", "TEST", RuleSeverity.RED, "告警", "金额超限"));

            cache.put(context, results);

            List<RuleResult> cached = cache.get(context);
            assertThat(cached).isNotNull();
            assertThat(cached).hasSize(1);
            assertThat(cached.get(0).getRuleCode()).isEqualTo("R001");
        }

        @Test
        @DisplayName("不同事实数据 - 缓存未命中")
        void shouldMissOnDifferentFacts() {
            Map<String, Object> facts1 = new HashMap<>();
            facts1.put("amount", 1000);
            RuleContext ctx1 = RuleContext.of(facts1, "TEST", "UNIT_TEST");

            Map<String, Object> facts2 = new HashMap<>();
            facts2.put("amount", 2000);
            RuleContext ctx2 = RuleContext.of(facts2, "TEST", "UNIT_TEST");

            cache.put(ctx1, Arrays.asList(RuleResult.triggered("R001", "R1", "T", RuleSeverity.RED, "T", "D")));

            List<RuleResult> cached = cache.get(ctx2);
            assertThat(cached).isNull();
        }

        @Test
        @DisplayName("不同场景 - 缓存未命中")
        void shouldMissOnDifferentScenario() {
            Map<String, Object> facts = new HashMap<>();
            facts.put("amount", 1000);

            RuleContext ctx1 = RuleContext.of(facts, "SCENARIO_A", "UNIT_TEST");
            RuleContext ctx2 = RuleContext.of(facts, "SCENARIO_B", "UNIT_TEST");

            cache.put(ctx1, Arrays.asList(RuleResult.triggered("R001", "R1", "T", RuleSeverity.RED, "T", "D")));

            assertThat(cache.get(ctx2)).isNull();
        }

        @Test
        @DisplayName("相同事实不同顺序 - 缓存命中（排序后哈希）")
        void shouldHitOnSameFactsDifferentOrder() {
            Map<String, Object> facts1 = new HashMap<>();
            facts1.put("a", 1);
            facts1.put("b", 2);
            facts1.put("c", 3);

            Map<String, Object> facts2 = new HashMap<>();
            facts2.put("c", 3);
            facts2.put("a", 1);
            facts2.put("b", 2);

            RuleContext ctx1 = RuleContext.of(facts1, "TEST", "UNIT_TEST");
            RuleContext ctx2 = RuleContext.of(facts2, "TEST", "UNIT_TEST");

            cache.put(ctx1, Arrays.asList(RuleResult.triggered("R001", "R1", "T", RuleSeverity.RED, "T", "D")));

            assertThat(cache.get(ctx2)).isNotNull();
        }

        @Test
        @DisplayName("空缓存 - 未命中")
        void shouldMissOnEmptyCache() {
            Map<String, Object> facts = new HashMap<>();
            facts.put("amount", 1000);
            RuleContext context = RuleContext.of(facts);

            assertThat(cache.get(context)).isNull();
            assertThat(cache.getMissCount()).isEqualTo(1);
            assertThat(cache.getHitCount()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("TTL 过期")
    class TtlExpiryTest {

        @Test
        @DisplayName("TTL 过期后 - 缓存未命中")
        void shouldExpireAfterTtl() throws InterruptedException {
            EvaluationResultCache shortTtlCache = new EvaluationResultCache(100L, 100);

            Map<String, Object> facts = new HashMap<>();
            facts.put("amount", 1000);
            RuleContext context = RuleContext.of(facts);

            shortTtlCache.put(context, Arrays.asList(
                    RuleResult.triggered("R001", "R1", "T", RuleSeverity.RED, "T", "D")));

            // 立即获取 - 命中
            assertThat(shortTtlCache.get(context)).isNotNull();

            // 等待 TTL 过期
            Thread.sleep(150);

            // 再次获取 - 未命中
            assertThat(shortTtlCache.get(context)).isNull();
        }
    }

    @Nested
    @DisplayName("LRU 淘汰")
    class LruEvictionTest {

        @Test
        @DisplayName("超过 maxSize - 淘汰最旧条目")
        void shouldEvictOldestWhenMaxSizeExceeded() {
            EvaluationResultCache smallCache = new EvaluationResultCache(60_000L, 3);

            for (int i = 0; i < 5; i++) {
                Map<String, Object> facts = new HashMap<>();
                facts.put("index", i);
                RuleContext ctx = RuleContext.of(facts);
                smallCache.put(ctx, Arrays.asList(
                        RuleResult.triggered("R" + i, "R" + i, "T", RuleSeverity.RED, "T", "D")));
            }

            assertThat(smallCache.size()).isEqualTo(3);
            assertThat(smallCache.getEvictionCount()).isEqualTo(2);

            // 前两个应该被淘汰
            Map<String, Object> facts0 = new HashMap<>();
            facts0.put("index", 0);
            assertThat(smallCache.get(RuleContext.of(facts0))).isNull();

            // 最后三个应该存在
            Map<String, Object> facts4 = new HashMap<>();
            facts4.put("index", 4);
            assertThat(smallCache.get(RuleContext.of(facts4))).isNotNull();
        }
    }

    @Nested
    @DisplayName("统计指标")
    class StatisticsTest {

        @Test
        @DisplayName("命中率和计数正确")
        void shouldCalculateHitRate() {
            Map<String, Object> facts = new HashMap<>();
            facts.put("amount", 1000);
            RuleContext context = RuleContext.of(facts);

            cache.put(context, Arrays.asList(
                    RuleResult.triggered("R001", "R1", "T", RuleSeverity.RED, "T", "D")));

            // 命中 2 次
            cache.get(context);
            cache.get(context);

            // 未命中 1 次
            Map<String, Object> facts2 = new HashMap<>();
            facts2.put("amount", 2000);
            cache.get(RuleContext.of(facts2));

            assertThat(cache.getHitCount()).isEqualTo(2);
            assertThat(cache.getMissCount()).isEqualTo(1);
            assertThat(cache.getHitRate()).isCloseTo(0.6667, within(0.01));
        }

        @Test
        @DisplayName("统计摘要包含关键信息")
        void shouldGenerateStatsSummary() {
            cache.put(RuleContext.of(new HashMap<>(Map.of("k", "v"))),
                    Arrays.asList(RuleResult.notTriggered("R001")));
            cache.get(RuleContext.of(new HashMap<>(Map.of("k", "v"))));

            String summary = cache.getStatsSummary();
            assertThat(summary).contains("hits=1");
            assertThat(summary).contains("hitRate=");
        }
    }

    @Nested
    @DisplayName("缓存管理")
    class CacheManagementTest {

        @Test
        @DisplayName("clear - 清空全部缓存")
        void shouldClearAll() {
            Map<String, Object> facts = new HashMap<>();
            facts.put("amount", 1000);
            RuleContext context = RuleContext.of(facts);

            cache.put(context, Arrays.asList(
                    RuleResult.triggered("R001", "R1", "T", RuleSeverity.RED, "T", "D")));
            assertThat(cache.size()).isEqualTo(1);

            cache.clear();
            assertThat(cache.size()).isEqualTo(0);
        }

        @Test
        @DisplayName("put null 结果 - 不缓存")
        void shouldNotCacheNullResults() {
            Map<String, Object> facts = new HashMap<>();
            facts.put("amount", 1000);
            RuleContext context = RuleContext.of(facts);

            cache.put(context, null);
            assertThat(cache.size()).isEqualTo(0);
        }

        @Test
        @DisplayName("返回的缓存结果是防御性副本")
        void shouldReturnDefensiveCopy() {
            Map<String, Object> facts = new HashMap<>();
            facts.put("amount", 1000);
            RuleContext context = RuleContext.of(facts);

            List<RuleResult> original = new java.util.ArrayList<>();
            original.add(RuleResult.triggered("R001", "R1", "T", RuleSeverity.RED, "T", "D"));

            cache.put(context, original);

            List<RuleResult> cached = cache.get(context);
            assertThat(cached).isNotNull();

            // 修改副本不影响缓存
            cached.clear();

            List<RuleResult> cachedAgain = cache.get(context);
            assertThat(cachedAgain).hasSize(1);
        }
    }

    private static org.assertj.core.data.Offset<Double> within(double tolerance) {
        return org.assertj.core.data.Offset.offset(tolerance);
    }
}
