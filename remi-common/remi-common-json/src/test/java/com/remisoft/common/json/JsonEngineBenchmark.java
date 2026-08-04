package com.remisoft.common.json;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import com.remisoft.common.json.cache.SerializerCache;
import com.remisoft.common.json.internal.JsonConfig;
import com.remisoft.common.json.naming.PropertyNamingStrategy;
import com.remisoft.common.json.provider.SerializationProvider;
import com.remisoft.common.json.testbean.AnnotationBean;
import com.remisoft.common.json.testbean.NamingBean;

/**
 * 综合 JMH 性能基准（P1-3）
 *
 * <p>覆盖 YdszJson 引擎的核心性能维度：</p>
 * <ul>
 *   <li>序列化性能（简单 Bean、注解 Bean、浮点精度、命名策略）</li>
 *   <li>反序列化性能（Map 解析、复杂嵌套 Bean）</li>
 *   <li>缓存命中 vs 未命中对比</li>
 *   <li>命名策略计算开销</li>
 *   <li>ThreadLocalSnapshot save/restore 开销</li>
 * </ul>
 *
 * <p>运行方式：</p>
 * <pre>
 *   cd remi-common-json
 *   mvn package -DskipTests
 *   java -jar target/benchmarks.jar
 * </pre>
 *
 * @since 1.0.0
 */
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 3)
@Fork(value = 2, jvmArgsAppend = {"-Xms512m", "-Xmx512m"})
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Benchmark)
public class JsonEngineBenchmark {

    // ==================== 测试用内部 Bean ====================

    /**
     * 简单 5 字段 Bean，用于基础序列化基准测试。
     */
    public static class SimpleBean5 {
        private int id;
        private String name;
        private double score;
        private boolean active;
        private long createdAt;

        public SimpleBean5() {}

        public int getId() { return id; }
        public void setId(int id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public double getScore() { return score; }
        public void setScore(double score) { this.score = score; }
        public boolean getActive() { return active; }
        public void setActive(boolean active) { this.active = active; }
        public long getCreatedAt() { return createdAt; }
        public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    }

    /**
     * 复杂嵌套 Bean：含嵌套对象、List、Map 字段，用于反序列化基准测试。
     */
    public static class NestedBean {
        private int id;
        private String name;
        private NestedAddress address;
        private List<String> tags;
        private Map<String, BigDecimal> scores;

        public NestedBean() {}

        public int getId() { return id; }
        public void setId(int id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public NestedAddress getAddress() { return address; }
        public void setAddress(NestedAddress address) { this.address = address; }
        public List<String> getTags() { return tags; }
        public void setTags(List<String> tags) { this.tags = tags; }
        public Map<String, BigDecimal> getScores() { return scores; }
        public void setScores(Map<String, BigDecimal> scores) { this.scores = scores; }
    }

    public static class NestedAddress {
        private String city;
        private String street;

        public NestedAddress() {}
        public String getCity() { return city; }
        public void setCity(String city) { this.city = city; }
        public String getStreet() { return street; }
        public void setStreet(String street) { this.street = street; }
    }

    // ==================== Benchmark 状态 ====================

    /** 默认 camelCase 映射器 */
    private JsonMapper camelMapper;
    /** snake_case 命名策略映射器 */
    private JsonMapper snakeMapper;
    /** useBigDecimal=true 映射器 */
    private JsonMapper bigDecimalMapper;

    /** 简单 5 字段 Bean 实例 */
    private SimpleBean5 simpleBean;
    /** NamingBean 实例（用于命名策略序列化） */
    private NamingBean namingBean;
    /** 注解 Bean 实例 */
    private AnnotationBean annotationBean;
    /** 复杂嵌套 Bean 实例 */
    private NestedBean nestedBean;

    /** 预生成的简单 JSON 字符串 */
    private String simpleJsonStr;
    /** 预生成的 Map JSON 字符串（用于 deserialize 基准） */
    private String mapJsonStr;
    /** 预生成的复杂嵌套 JSON 字符串 */
    private String nestedJsonStr;

    // ==================== Setup ====================

    @Setup(Level.Trial)
    public void setUp() {
        // 构建不同配置的 JsonMapper
        camelMapper = JsonMapper.builder()
                .namingStrategy(PropertyNamingStrategy.LOWER_CAMEL_CASE)
                .build();
        snakeMapper = JsonMapper.builder()
                .namingStrategy(PropertyNamingStrategy.SNAKE_CASE)
                .build();
        bigDecimalMapper = JsonMapper.builder()
                .useBigDecimal(true)
                .build();

        // 初始化测试数据
        simpleBean = new SimpleBean5();
        simpleBean.setId(42);
        simpleBean.setName("Alice");
        simpleBean.setScore(3.14159);
        simpleBean.setActive(true);
        simpleBean.setCreatedAt(1700000000000L);

        namingBean = new NamingBean();
        namingBean.setUserName("john_doe");
        namingBean.setUserId(10001);

        annotationBean = new AnnotationBean();
        annotationBean.setId(99L);
        annotationBean.setName("Bob");
        annotationBean.setPassword("secret");
        annotationBean.setBirthday(LocalDate.of(1990, 1, 15));
        annotationBean.setOptionalField("opt-value");
        annotationBean.setNonEmptyField("non-empty-data");
        annotationBean.setNonDefaultField(88);
        annotationBean.setRawData("{\"raw\":true}");
        annotationBean.setAddress(new AnnotationBean.EmbeddedAddress("Shanghai", "Nanjing Rd"));
        annotationBean.setScore(95);
        annotationBean.setPublicInfo("public-info");
        annotationBean.setInternalInfo("internal-info");
        annotationBean.setInternalField("should-be-hidden");

        nestedBean = new NestedBean();
        nestedBean.setId(1);
        nestedBean.setName("Charlie");
        NestedAddress addr = new NestedAddress();
        addr.setCity("Beijing");
        addr.setStreet("Chang'an Avenue");
        nestedBean.setAddress(addr);
        nestedBean.setTags(List.of("java", "json", "benchmark"));
        Map<String, BigDecimal> scores = new LinkedHashMap<>();
        scores.put("math", new BigDecimal("99.5"));
        scores.put("english", new BigDecimal("88.0"));
        nestedBean.setScores(scores);

        // 预生成 JSON 字符串（避免在 benchmark 方法内部分配）
        simpleJsonStr = camelMapper.toJson(simpleBean);
        mapJsonStr = "{\"id\":42,\"name\":\"Alice\",\"score\":3.14159,\"active\":true,\"timestamp\":1700000000000}";
        nestedJsonStr = "{\"id\":1,\"name\":\"Charlie\",\"address\":{\"city\":\"Beijing\",\"street\":\"Chang'an Avenue\"},\"tags\":[\"java\",\"json\",\"benchmark\"],\"scores\":{\"math\":99.5,\"english\":88.0}}";

        // 预热 ASM 序列化器（确保缓存已建立）
        camelMapper.warmup(SimpleBean5.class, NamingBean.class, AnnotationBean.class, NestedBean.class, NestedAddress.class);
        // 触发一次完整缓存填充
        camelMapper.toJson(simpleBean);
        camelMapper.toJson(namingBean);
        camelMapper.toJson(annotationBean);
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        SerializationProvider.clearThreadLocals();
    }

    // ==================== 1. 序列化性能 ====================

    /**
     * 简单 5 字段 Bean 序列化（camelCase 命名策略，默认配置）。
     */
    @Benchmark
    public void serialize5_simpleBean_camelCase(Blackhole bh) {
        bh.consume(camelMapper.toJson(simpleBean));
    }

    /**
     * 简单 5 字段 Bean 序列化（SNAKE_CASE 命名策略）。
     */
    @Benchmark
    public void serialize5_simpleBean_snakeCase(Blackhole bh) {
        bh.consume(snakeMapper.toJson(simpleBean));
    }

    /**
     * NamingBean 序列化（SNAKE_CASE 命名策略，验证 userName → user_name 转换开销）。
     */
    @Benchmark
    public void serialize_namingBean_snakeCase(Blackhole bh) {
        bh.consume(snakeMapper.toJson(namingBean));
    }

    /**
     * 含 JSON 注解的 Bean 序列化（@JsonProperty, @JsonIgnore, @JsonFormat, @JsonUnwrapped 等）。
     */
    @Benchmark
    public void serialize_annotationBean(Blackhole bh) {
        bh.consume(camelMapper.toJson(annotationBean));
    }

    /**
     * 浮点数字段序列化（useBigDecimal=true，走 BigDecimal 精度路径）。
     */
    @Benchmark
    public void serialize_floatPrecision_bigDecimal(Blackhole bh) {
        bh.consume(bigDecimalMapper.toJson(simpleBean));
    }

    // ==================== 2. 反序列化性能 ====================

    /**
     * 简单 JSON → Map&lt;String, Object&gt; 反序列化（useBigDecimal=false，默认 double 路径）。
     */
    @Benchmark
    public void deserialize_map_double(Blackhole bh) {
        bh.consume(camelMapper.parseMap(mapJsonStr));
    }

    /**
     * 简单 JSON → Map&lt;String, Object&gt; 反序列化（useBigDecimal=true，BigDecimal 精度路径）。
     */
    @Benchmark
    public void deserialize_map_bigDecimal(Blackhole bh) {
        bh.consume(bigDecimalMapper.parseMap(mapJsonStr));
    }

    /**
     * 复杂嵌套 JSON → Java Bean 反序列化（含嵌套对象、List、Map&lt;String, BigDecimal&gt;）。
     */
    @Benchmark
    public void deserialize_nestedBean(Blackhole bh) {
        bh.consume(camelMapper.toObject(nestedJsonStr, NestedBean.class));
    }

    // ==================== 3. 缓存命中 vs 未命中 ====================

    /**
     * 缓存命中场景：序列化已经预热过的类型。
     * @Setup(Level.Trial) 中已触发过序列化，所有 ASM 序列化器、
     * 字段元数据、Bean 信息均已缓存。
     */
    @Benchmark
    public void cacheHit_serializeSimpleBean(Blackhole bh) {
        bh.consume(camelMapper.toJson(simpleBean));
    }

    /**
     * 缓存未命中场景：每次调用前清空 SerializerCache，
     * 强制重新加载字段元数据 + 重建序列化器缓存。
     */
    @Benchmark
    public void cacheMiss_serializeSimpleBean(Blackhole bh) {
        SerializerCache.clear();
        bh.consume(camelMapper.toJson(simpleBean));
    }

    // ==================== 4. 命名策略计算 ====================

    /**
     * PropertyNamingStrategy.SNAKE_CASE.translate() 单独基准。
     */
    @Benchmark
    public void namingStrategy_snakeCase(Blackhole bh) {
        bh.consume(PropertyNamingStrategy.SNAKE_CASE.translate("userName"));
    }

    /**
     * PropertyNamingStrategy.LOWER_CAMEL_CASE.translate() 单独基准（直接返回的恒等函数）。
     */
    @Benchmark
    public void namingStrategy_camelCase(Blackhole bh) {
        bh.consume(PropertyNamingStrategy.LOWER_CAMEL_CASE.translate("userName"));
    }

    /**
     * SNAKE_CASE 更复杂的字段名转换（包含大写缩写边界）。
     */
    @Benchmark
    public void namingStrategy_snakeCase_complex(Blackhole bh) {
        bh.consume(PropertyNamingStrategy.SNAKE_CASE.translate("userID"));
        bh.consume(PropertyNamingStrategy.SNAKE_CASE.translate("JSONParserVersion"));
        bh.consume(PropertyNamingStrategy.SNAKE_CASE.translate("XMLHttpURL"));
    }

    // ==================== 5. ThreadLocalSnapshot 开销 ====================

    /**
     * 测量 new ThreadLocalSnapshot() + snapshot.restore() 这对操作的开销。
     * 这是每次序列化/反序列化调用中 ThreadLocal 上下文保存/恢复的成本。
     */
    @Benchmark
    public void threadLocalSnapshot_saveRestore(Blackhole bh) {
        SerializationProvider.ThreadLocalSnapshot snapshot = new SerializationProvider.ThreadLocalSnapshot();
        bh.consume(snapshot);
        snapshot.restore();
    }

    /**
     * ThreadLocalSnapshot 构造（仅保存，不含恢复）。
     */
    @Benchmark
    public void threadLocalSnapshot_constructOnly(Blackhole bh) {
        bh.consume(new SerializationProvider.ThreadLocalSnapshot());
    }

    /**
     * ThreadLocalSnapshot 恢复（仅恢复，不含构造）。
     */
    @Benchmark
    public void threadLocalSnapshot_restoreOnly(Blackhole bh) {
        SerializationProvider.ThreadLocalSnapshot snapshot = new SerializationProvider.ThreadLocalSnapshot();
        bh.consume(snapshot);
        snapshot.restore();
    }

    // ==================== main 方法（独立运行入口） ====================

    /**
     * 独立运行 JMH 基准的 main 方法。
     *
     * <p>通常通过 Maven 打包为 uber-jar 后执行：</p>
     * <pre>java -jar target/benchmarks.jar</pre>
     *
     * <p>也可以直接运行此类，JMH 会自动处理。</p>
     */
    public static void main(String[] args) throws Exception {
        org.openjdk.jmh.Main.main(args);
    }
}
