package com.njydsz.common.json;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

import com.njydsz.common.json.annotation.JsonClass;
import com.njydsz.common.json.annotation.JsonProperty;
import com.njydsz.common.json.config.JsonConfig;
import com.njydsz.common.json.naming.PropertyNamingStrategy;
import com.njydsz.common.json.provider.SerializationProvider;
import com.njydsz.common.json.testbean.NamingBean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P0 修复回归测试。
 *
 * <p>覆盖三项阻断性 Bug：</p>
 * <ol>
 *   <li>{@code JsonMapper.restoreConfig} 无限递归 → StackOverflowError（A1）</li>
 *   <li>{@code JsonMapper.configApplied} 跨线程误共享 → 共享 Mapper 配置错乱（A2）</li>
 *   <li>{@code ThreadLocalSnapshot} 未回滚 namingStrategy → 单次配置泄漏（A3）</li>
 * </ol>
 *
 * <p>A2/A3 通过直接校验 {@link SerializationProvider} 的 ThreadLocal 状态隔离来验证修复，
 * 避免与预存的"字段元数据加载时缓存 jsonName"等行为缺口耦合。</p>
 */
class JsonMapperP0RegressionTest {

    @BeforeEach
    void setUp() {
        JsonConfig.getInstance().reset();
        JsonConfig.getInstance().apply();
    }

    @AfterEach
    void tearDown() {
        JsonConfig.getInstance().reset();
        JsonConfig.getInstance().apply();
    }

    private static NamingBean sampleBean() {
        NamingBean b = new NamingBean();
        b.setUserName("alice");
        b.setUserId(7);
        return b;
    }

    // ==================== A1: restoreConfig 无限递归 ====================

    /**
     * A1 回归：此前 {@code new JsonMapper().toJson(obj)} 必然在 finally 中
     * 调用 {@code restoreConfig(snapshot)} → 自调用 → StackOverflowError。
     */
    @Test
    void defaultMapperToJsonDoesNotStackOverflow() {
        JsonMapper mapper = new JsonMapper();
        String json = assertDoesNotThrow(() -> mapper.toJson(sampleBean()));
        assertTrue(json.contains("\"userName\""), () -> "expected camelCase key, got: " + json);
    }

    @Test
    void defaultMapperPrettyToJsonDoesNotStackOverflow() {
        JsonMapper mapper = new JsonMapper();
        String json = assertDoesNotThrow(() -> mapper.toJson(sampleBean(), true));
        assertTrue(json.contains("\"userName\""));
        assertTrue(json.contains("\n"), () -> "pretty output should be multi-line, got: " + json);
    }

    @Test
    void defaultMapperToJsonBytesDoesNotStackOverflow() {
        JsonMapper mapper = new JsonMapper();
        byte[] bytes = assertDoesNotThrow(() -> mapper.toJsonBytes(sampleBean()));
        assertTrue(new String(bytes).contains("\"userName\""));
    }

    @Test
    void defaultMapperToJsonWithViewDoesNotStackOverflow() {
        JsonMapper mapper = new JsonMapper();
        assertDoesNotThrow(() -> mapper.toJson(sampleBean(), Object.class));
    }

    // ==================== A2: configApplied 跨线程误共享 ====================

    /**
     * A2 回归：此前 {@code configApplied} 是实例字段，共享 Mapper 第一次 apply 后置 true，
     * 后续调用跳过 applyConfigIfNeeded → ThreadLocal 未被设置。
     *
     * <p>修复后每次序列化都 apply/restore。校验：writeNulls Mapper 序列化期间
     * {@link SerializationProvider#isWriteNulls()} 为 true，序列化后恢复为 false。</p>
     */
    @Test
    void writeNullsMapperAppliesAndRestoresThreadLocalState() {
        JsonMapper writeNullsMapper = JsonMapper.builder().writeNulls(true).build();
        NamingBean b = sampleBean();

        // 序列化前：默认 writeNulls=false
        assertFalse(SerializationProvider.isWriteNulls(),
            "before mapper call, global writeNulls should be false");

        // 序列化：Mapper 内部 apply writeNulls=true，调用结束 restore 回 false
        writeNullsMapper.toJson(b);

        // 序列化后：ThreadLocal 应回滚到默认 false（A2 修复点：不再因 configApplied 跳过 restore）
        assertFalse(SerializationProvider.isWriteNulls(),
            "after mapper call, writeNulls must be restored to false (A2 regression)");
    }

    /**
     * A2 回归（跨线程）：共享 Mapper 在多线程并发使用时，各线程的 ThreadLocal 配置应正确隔离。
     *
     * <p>此前 configApplied 跨线程误共享：线程 A apply 后置 true，线程 B 见 true 跳过 apply。
     * 修复后每个线程的每次调用都独立 apply/restore。</p>
     */
    @Test
    void sharedWriteNullsMapperWorksAcrossThreads() throws InterruptedException {
        JsonMapper writeNullsMapper = JsonMapper.builder().writeNulls(true).build();
        int threads = 8;
        int iterations = 50;
        CountDownLatch latch = new CountDownLatch(threads);
        AtomicReference<Throwable> error = new AtomicReference<>();

        for (int t = 0; t < threads; t++) {
            Thread worker = new Thread(() -> {
                try {
                    NamingBean b = sampleBean();
                    for (int i = 0; i < iterations; i++) {
                        // 序列化期间 writeNulls 应为 true；序列化后应回滚
                        writeNullsMapper.toJson(b);
                        // 调用结束后 ThreadLocal 应已 restore（A2 隔离）
                        if (SerializationProvider.isWriteNulls()) {
                            throw new AssertionError(
                                "writeNulls leaked after mapper call on thread (A2 regression)");
                        }
                    }
                } catch (Throwable ex) {
                    error.compareAndSet(null, ex);
                } finally {
                    latch.countDown();
                }
            }, "json-mapper-test-" + t);
            worker.start();
        }

        latch.await();
        if (error.get() != null) {
            throw new AssertionError("concurrent mapper test failed: " + error.get().getMessage(), error.get());
        }
    }

    /**
     * A2 隔离验证：writeNulls Mapper 不应污染全局默认 ThreadLocal 状态。
     */
    @Test
    void writeNullsMapperDoesNotLeakToGlobalDefault() {
        NamingBean b = sampleBean();
        JsonMapper writeNullsMapper = JsonMapper.builder().writeNulls(true).build();
        writeNullsMapper.toJson(b);

        assertFalse(SerializationProvider.isWriteNulls(),
            "global default writeNulls must remain false after mapper call (A2 leak)");
    }

    /**
     * A2 行为验证（带注解 Bean）：writeNulls=true 的 Mapper 序列化带注解 Bean 时，
     * null 字段应被输出（覆盖 ValueWriter 注解路径的 writeNulls 配置生效修复）。
     */
    @Test
    void writeNullsMapperOutputsNullForAnnotatedBean() {
        AnnotatedBean b = new AnnotatedBean();
        b.setId(9);
        // name 故意留 null

        JsonMapper writeNullsMapper = JsonMapper.builder().writeNulls(true).build();
        String json = writeNullsMapper.toJson(b);
        assertTrue(json.contains("\"name\":null"),
            () -> "writeNulls=true should output null field for annotated bean, got: " + json);

        // 默认 Mapper（writeNulls=false）不应输出 null
        JsonMapper defaultMapper = new JsonMapper();
        String defaultJson = defaultMapper.toJson(b);
        assertFalse(defaultJson.contains("\"name\":null"),
            () -> "default mapper should NOT output null, got: " + defaultJson);
    }

    // ==================== A3: namingStrategy 单次配置泄漏 ====================

    /**
     * A3 回归：{@link YdszJson#toJson(Object, JsonConfig)} 用 SNAKE_CASE 配置后，
     * ThreadLocalSnapshot 必须回滚 namingStrategy，否则后续默认调用泄漏为 SNAKE_CASE。
     *
     * <p>校验 {@link SerializationProvider#getNamingStrategy()} 在单次配置调用后恢复为默认值。
     * （注：字段 jsonName 在元数据加载时缓存，故行为层面的逐调用命名对已缓存类无效；
     * 但 ThreadLocal 泄漏会导致"调用期间新加载的类"被永久缓存错误命名，A3 修复消除该污染。）</p>
     */
    @Test
    void singleShotSnakeCaseConfigRestoresNamingStrategyThreadLocal() {
        NamingBean b = sampleBean();
        PropertyNamingStrategy defaultStrategy = SerializationProvider.getNamingStrategy();

        JsonConfig snakeConfig = JsonConfig.builder()
            .namingStrategy(PropertyNamingStrategy.SNAKE_CASE)
            .build();

        // 单次 snake 配置序列化
        YdszJson.toJson(b, snakeConfig);

        // ThreadLocal namingStrategy 必须恢复为默认（A3 修复点）
        assertEquals(defaultStrategy, SerializationProvider.getNamingStrategy(),
            "namingStrategy ThreadLocal must be restored after single-shot config (A3 leak)");
    }

    /**
     * A3 回归（连续多次单次配置）：交替使用默认与 snake_case 配置，确保每次都正确回滚。
     */
    @Test
    void repeatedSingleShotConfigsRestoreNamingStrategy() {
        NamingBean b = sampleBean();
        PropertyNamingStrategy defaultStrategy = SerializationProvider.getNamingStrategy();
        JsonConfig snakeConfig = JsonConfig.builder()
            .namingStrategy(PropertyNamingStrategy.SNAKE_CASE)
            .build();

        for (int i = 0; i < 5; i++) {
            YdszJson.toJson(b, snakeConfig);
            assertEquals(defaultStrategy, SerializationProvider.getNamingStrategy(),
                "iter " + i + ": namingStrategy must be restored (A3 leak)");
        }
    }

    /**
     * A3 回归（JsonMapper 实例）：snake_case Mapper 序列化后，全局 namingStrategy 不被污染。
     */
    @Test
    void mapperInstanceNamingStrategyDoesNotLeakToGlobal() {
        NamingBean b = sampleBean();
        PropertyNamingStrategy defaultStrategy = SerializationProvider.getNamingStrategy();

        JsonMapper snakeMapper = JsonMapper.builder()
            .namingStrategy(PropertyNamingStrategy.SNAKE_CASE)
            .build();
        snakeMapper.toJson(b);

        assertEquals(defaultStrategy, SerializationProvider.getNamingStrategy(),
            "global namingStrategy must not be polluted by snake_case mapper (A3 leak)");
    }

    /**
     * A2 兼容性：{@link JsonMapper#configChanged()} 现为 no-op，调用应无害。
     */
    @Test
    void configChangedIsNowHarmlessNoOp() {
        JsonMapper mapper = JsonMapper.builder().writeNulls(true).build();
        mapper.configChanged();

        // no-op 不应破坏 apply/restore：序列化期间 writeNulls=true，之后恢复 false
        mapper.toJson(sampleBean());
        assertFalse(SerializationProvider.isWriteNulls(),
            "writeNulls must be restored after configChanged() + toJson (A2)");
    }

    /** 带 @JsonClass 的 Bean，强制走 ValueWriter 注解路径（使 writeNulls 配置生效）。
     *  注：@JsonClass(writeNulls) 默认 false，writeNulls 行为来自 JsonMapper 配置。 */
    @JsonClass
    static class AnnotatedBean {
        @JsonProperty("id")
        private int id;
        @JsonProperty("name")
        private String name;

        public int getId() { return id; }
        public void setId(int id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
    }
}
