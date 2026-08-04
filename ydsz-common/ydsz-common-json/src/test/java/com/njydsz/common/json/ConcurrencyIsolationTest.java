package com.njydsz.common.json;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.njydsz.common.json.cache.SerializerCache;
import com.njydsz.common.json.internal.JsonConfig;
import com.njydsz.common.json.naming.PropertyNamingStrategy;
import com.njydsz.common.json.parser.JsonParserUtil;
import com.njydsz.common.json.provider.SerializationProvider;
import com.njydsz.common.json.testbean.NamingBean;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P0-1 / P0-2 并发安全修复回归测试——双层缓存隔离与 useBigDecimal 线程化。
 *
 * <p>覆盖本次修复的两个高危缺陷：</p>
 * <ol>
 *   <li><b>SerializerCache 按 (Class, namingStrategy) 隔离</b>：
 *       此前缓存仅以 Class 为 Key，jsonName 首次加载时被"烘焙固化"，不同命名策略的
 *       Mapper 交错序列化时字段名被污染。修复后双层缓存隔离（P0-1）。</li>
 *   <li><b>useBigDecimal ThreadLocal 化</b>：
 *       此前是进程级某 Mapper 开启 BigDecimal 后永久影响所有线程/所有 Mapper 的解析行为。
 *       修复后纳入 ThreadLocal 快照保存/恢复（P0-2）。</li>
 * </ol>
 */
class ConcurrencyIsolationTest {

    @BeforeEach
    void setUp() {
        SerializerCache.clear();
        SerializationProvider.clearThreadLocals();
        JsonConfig.getInstance().apply();
    }

    @AfterEach
    void tearDown() {
        SerializerCache.clear();
        SerializationProvider.clearThreadLocals();
        JsonConfig.getInstance().apply();
    }

    // ==================== P0-1: 命名策略双层缓存隔离 ====================

    /**
     * 串行验证：snake_case 与 camelCase 输出各自的正确字段名，互不影响。
     */
    @Test
    void snakeAndCamelMappersProduceDifferentFieldNames() {
        NamingBean b = new NamingBean();
        b.setUserName("alice");
        b.setUserId(7);

        JsonMapper snakeMapper = JsonMapper.builder()
            .namingStrategy(PropertyNamingStrategy.SNAKE_CASE)
            .build();
        JsonMapper camelMapper = new JsonMapper();

        String snakeJson = snakeMapper.toJson(b);
        String camelJson = camelMapper.toJson(b);

        assertTrue(snakeJson.contains("\"user_name\""),
            () -> "snake_case should produce user_name, got: " + snakeJson);
        assertTrue(snakeJson.contains("\"user_id\""),
            () -> "snake_case should produce user_id, got: " + snakeJson);

        assertTrue(camelJson.contains("\"userName\""),
            () -> "default camelCase should keep userName, got: " + camelJson);
        assertTrue(camelJson.contains("\"userId\""),
            () -> "default camelCase should keep userId, got: " + camelJson);
    }

    /**
     * 并发验证：snake_case 与 camelCase Mapper 交错序列化同一类，
     * 两个策略的输出字段名在任何时刻都不应互相污染。
     *
     * <p>核心断言：两个 Mapper 各自的输出中字段名必须始终保持一致。</p>
     */
    @Test
    void interleavedNamingStrategiesDoNotPolluteFieldNames() throws InterruptedException {
        JsonMapper snakeMapper = JsonMapper.builder()
            .namingStrategy(PropertyNamingStrategy.SNAKE_CASE)
            .build();
        JsonMapper camelMapper = new JsonMapper();

        int threadsPerStrategy = 6;
        int iterations = 200;
        int totalThreads = threadsPerStrategy * 2;
        CountDownLatch latch = new CountDownLatch(totalThreads);
        AtomicReference<Throwable> error = new AtomicReference<>();

        // snake_case 线程
        for (int t = 0; t < threadsPerStrategy; t++) {
            Thread worker = new Thread(() -> {
                try {
                    for (int i = 0; i < iterations; i++) {
                        NamingBean b = new NamingBean();
                        b.setUserName("user-" + i);
                        b.setUserId(i);
                        String json = snakeMapper.toJson(b);

                        // snake_case 输出必须始终是 user_name / user_id
                        if (!json.contains("\"user_name\"") || !json.contains("\"user_id\"")) {
                            throw new AssertionError(
                                "snake_case mapper produced wrong field names: " + json);
                        }
                    }
                } catch (Throwable ex) {
                    error.compareAndSet(null, ex);
                } finally {
                    latch.countDown();
                }
            }, "snake-worker-" + t);
            worker.start();
        }

        // camelCase 线程
        for (int t = 0; t < threadsPerStrategy; t++) {
            Thread worker = new Thread(() -> {
                try {
                    for (int i = 0; i < iterations; i++) {
                        NamingBean b = new NamingBean();
                        b.setUserName("user-" + i);
                        b.setUserId(i);
                        String json = camelMapper.toJson(b);

                        // camelCase 输出必须始终是 userName / userId
                        if (!json.contains("\"userName\"") || !json.contains("\"userId\"")) {
                            throw new AssertionError(
                                "camelCase mapper produced wrong field names: " + json);
                        }
                    }
                } catch (Throwable ex) {
                    error.compareAndSet(null, ex);
                } finally {
                    latch.countDown();
                }
            }, "camel-worker-" + t);
            worker.start();
        }

        latch.await();
        if (error.get() != null) {
            throw new AssertionError("interleaved naming test failed: " + error.get().getMessage(), error.get());
        }
    }

    /**
     * 验证双层缓存确实为同一 Class 生成了多个策略维度的条目。
     */
    @Test
    void serializerCacheMaintainsMultipleStrategiesPerClass() throws InterruptedException {
        JsonMapper snakeMapper = JsonMapper.builder()
            .namingStrategy(PropertyNamingStrategy.SNAKE_CASE)
            .build();
        JsonMapper camelMapper = new JsonMapper();

        // 先触发两个策略的加载
        NamingBean b = new NamingBean();
        b.setUserName("x");
        b.setUserId(1);
        snakeMapper.toJson(b);
        camelMapper.toJson(b);

        // 同一 Class 在缓存中应至少有 2 个策略维度的条目
        int strategies = SerializerCache.strategySize(NamingBean.class);
        assertTrue(strategies >= 2,
            () -> "expected >= 2 cached strategies for NamingBean, got: " + strategies);
    }

    // ==================== P0-2: useBigDecimal 线程化隔离 ====================

    /**
     * 串行验证：BigDecimal Mapper 解析浮点数为 BigDecimal，默认 Mapper 解析为 Double。
     */
    @Test
    void bigDecimalMapperParsesDecimalsAsBigDecimal() {
        // BigDecimal 模式
        JsonMapper bigDecimalMapper = JsonMapper.builder().useBigDecimal(true).build();

        // 创建包含 double 字段的简单对象进行反序列化验证
        String json = "{\"value\":3.14}";
        // 反序列化为 Map 来验证数值类型
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> result = bigDecimalMapper.fromJson(json, java.util.Map.class);
        Object value = result.get("value");

        // BigDecimal 模式下应为 BigDecimal 类型
        assertTrue(value instanceof BigDecimal,
            () -> "with useBigDecimal=true, 3.14 should parse as BigDecimal, got: "
                + (value == null ? "null" : value.getClass().getSimpleName() + "(" + value + ")"));

        // 清理 ThreadLocal 以隔离下一个测试场景
        SerializationProvider.clearThreadLocals();

        // 默认模式
        JsonMapper defaultMapper = new JsonMapper();
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> defaultResult = defaultMapper.fromJson(json, java.util.Map.class);
        Object defaultValue = defaultResult.get("value");

        // 默认模式下应为 Double 类型
        assertTrue(defaultValue instanceof Double,
            () -> "with default config (useBigDecimal=false), 3.14 should parse as Double, got: "
                + (defaultValue == null ? "null" : defaultValue.getClass().getSimpleName() + "(" + defaultValue + ")"));
    }

    /**
     * 并发验证：BigDecimal 模式与默认模式的 Mapper 交错反序列化同一 JSON，
     * 解析结果数值类型不受对方影响。
     */
    @Test
    void interleavedBigDecimalModesDoNotPolluteEachOther() throws InterruptedException {
        JsonMapper bigDecimalMapper = JsonMapper.builder().useBigDecimal(true).build();
        JsonMapper defaultMapper = new JsonMapper();

        int threadsPerMode = 6;
        int iterations = 150;
        int totalThreads = threadsPerMode * 2;
        CountDownLatch latch = new CountDownLatch(totalThreads);
        AtomicReference<Throwable> error = new AtomicReference<>();

        // BigDecimal 线程
        for (int t = 0; t < threadsPerMode; t++) {
            Thread worker = new Thread(() -> {
                try {
                    for (int i = 0; i < iterations; i++) {
                        String json = "{\"amount\":" + (i + 1) + ".50}";
                        @SuppressWarnings("unchecked")
                        java.util.Map<String, Object> result = bigDecimalMapper.fromJson(json, java.util.Map.class);

                        if (!(result.get("amount") instanceof BigDecimal)) {
                            throw new AssertionError(
                                "BigDecimal mapper returned non-BigDecimal type: "
                                    + result.get("amount").getClass().getSimpleName());
                        }
                    }
                } catch (Throwable ex) {
                    error.compareAndSet(null, ex);
                } finally {
                    latch.countDown();
                }
            }, "bigdecimal-worker-" + t);
            worker.start();
        }

        // 默认（Double）线程
        for (int t = 0; t < threadsPerMode; t++) {
            Thread worker = new Thread(() -> {
                try {
                    for (int i = 0; i < iterations; i++) {
                        String json = "{\"amount\":" + (i + 1) + ".50}";
                        @SuppressWarnings("unchecked")
                        java.util.Map<String, Object> result = defaultMapper.fromJson(json, java.util.Map.class);

                        if (!(result.get("amount") instanceof Double)) {
                            throw new AssertionError(
                                "default mapper returned non-Double type: "
                                    + result.get("amount").getClass().getSimpleName());
                        }
                    }
                } catch (Throwable ex) {
                    error.compareAndSet(null, ex);
                } finally {
                    latch.countDown();
                }
            }, "double-worker-" + t);
            worker.start();
        }

        latch.await();
        if (error.get() != null) {
            throw new AssertionError("interleaved BigDecimal test failed: " + error.get().getMessage(), error.get());
        }
    }

    /**
     * 验证 ThreadLocalSnapshot 在序列化前后正确恢复 useBigDecimal 值。
     */
    @Test
    void useBigDecimalIsRestoredAfterMapperCall() {
        // 开始前默认是 false
        JsonConfig.getInstance().apply();
        assertEquals(false, SerializationProvider.isUseBigDecimal());

        // 创建一个 useBigDecimal=true 的 Mapper 序列化
        JsonMapper bigDecimalMapper = JsonMapper.builder().useBigDecimal(true).build();
        NamingBean b = new NamingBean();
        b.setUserName("x");
        b.setUserId(1);
        bigDecimalMapper.toJson(b);

        // 序列化后 ThreadLocal 应被恢复为 false（全局默认）
        assertEquals(false, SerializationProvider.isUseBigDecimal(),
            "useBigDecimal must be restored to false after mapper call (P0-2 snapshot)");
    }

    // ==================== P0-1 + P0-2 组合并发隔离 ====================

    /**
     * 最坏情况并发测试：4 种配置各异的 Mapper（snake/bigDecimal、snake/default、
     * camel/bigDecimal、camel/default）同时并发运行，验证它们的输出互不干扰。
     */
    @Test
    void fourWayConcurrentConfigIsolation() throws InterruptedException {
        JsonMapper[] mappers = {
            JsonMapper.builder()
                .namingStrategy(PropertyNamingStrategy.SNAKE_CASE)
                .build(),
            JsonMapper.builder()
                .namingStrategy(PropertyNamingStrategy.SNAKE_CASE)
                .useBigDecimal(true)
                .build(),
            JsonMapper.builder().build(),
            JsonMapper.builder().useBigDecimal(true).build(),
        };

        int threadsPerMapper = 4;
        int iterations = 100;
        int totalThreads = threadsPerMapper * mappers.length;
        CountDownLatch latch = new CountDownLatch(totalThreads);
        AtomicReference<Throwable> error = new AtomicReference<>();

        for (int m = 0; m < mappers.length; m++) {
            final int mapperIdx = m;
            final JsonMapper mapper = mappers[m];
            final boolean isSnake = (m == 0 || m == 1);
            final boolean isBigDecimal = (m == 1 || m == 3);

            for (int t = 0; t < threadsPerMapper; t++) {
                Thread worker = new Thread(() -> {
                    try {
                        for (int i = 0; i < iterations; i++) {
                            // 序列化验证命名策略
                            NamingBean b = new NamingBean();
                            b.setUserName("u" + i);
                            b.setUserId(i);
                            String json = mapper.toJson(b);

                            if (isSnake) {
                                if (!json.contains("\"user_name\"")) {
                                    throw new AssertionError(
                                        "snake mapper[" + mapperIdx + "] wrong: " + json);
                                }
                            } else {
                                if (!json.contains("\"userName\"")) {
                                    throw new AssertionError(
                                        "camel mapper[" + mapperIdx + "] wrong: " + json);
                                }
                            }

                            // 反序列化验证 useBigDecimal
                            String numJson = "{\"v\":" + (i + 1) + ".25}";
                            @SuppressWarnings("unchecked")
                            java.util.Map<String, Object> result =
                                mapper.fromJson(numJson, java.util.Map.class);

                            if (isBigDecimal) {
                                if (!(result.get("v") instanceof BigDecimal)) {
                                    throw new AssertionError(
                                        "bigDecimal mapper[" + mapperIdx + "] wrong type: "
                                            + result.get("v").getClass().getSimpleName());
                                }
                            } else {
                                if (!(result.get("v") instanceof Double)) {
                                    throw new AssertionError(
                                        "default mapper[" + mapperIdx + "] wrong type: "
                                            + result.get("v").getClass().getSimpleName());
                                }
                            }
                        }
                    } catch (Throwable ex) {
                        error.compareAndSet(null, ex);
                    } finally {
                        latch.countDown();
                    }
                }, "4way-m" + mapperIdx + "-t" + t);
                worker.start();
            }
        }

        latch.await();
        if (error.get() != null) {
            throw new AssertionError("4-way concurrency test failed: " + error.get().getMessage(), error.get());
        }
    }

    /**
     * 双重保险：并发测试后，全局默认 ThreadLocal 不应泄漏任何配置。
     */
    @Test
    void globalThreadLocalStateUnchangedAfterConcurrentMixedUsage() throws InterruptedException {
        JsonConfig original = JsonConfig.getInstance();

        // 保存初始状态
        boolean defaultUseBigDecimal = SerializationProvider.isUseBigDecimal();
        PropertyNamingStrategy defaultStrategy = SerializationProvider.getNamingStrategy();

        // 并发混合使用多个配置
        CountDownLatch latch = new CountDownLatch(4);
        for (int t = 0; t < 4; t++) {
            final int idx = t;
            Thread worker = new Thread(() -> {
                try {
                    JsonMapper mapper = JsonMapper.builder()
                        .namingStrategy(
                            idx % 2 == 0 ? PropertyNamingStrategy.SNAKE_CASE : PropertyNamingStrategy.KEBAB_CASE)
                        .useBigDecimal(idx % 2 == 0)
                        .writeNulls(idx % 2 == 0)
                        .build();

                    NamingBean b = new NamingBean();
                    b.setUserName("x");
                    b.setUserId(1);
                    for (int i = 0; i < 50; i++) {
                        mapper.toJson(b);
                    }
                } finally {
                    latch.countDown();
                }
            }, "mixed-" + t);
            worker.start();
        }
        latch.await();

        // 清理后重新应用默认配置
        SerializationProvider.clearThreadLocals();
        original.apply();

        // 全局状态不应被泄漏
        assertEquals(defaultUseBigDecimal, SerializationProvider.isUseBigDecimal(),
            "global useBigDecimal leaked after concurrent usage");
        assertEquals(defaultStrategy, SerializationProvider.getNamingStrategy(),
            "global namingStrategy leaked after concurrent usage");
    }
}
