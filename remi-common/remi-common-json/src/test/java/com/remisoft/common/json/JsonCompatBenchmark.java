package com.remisoft.common.json;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import com.alibaba.fastjson2.JSON;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * JSON 跨库性能对比基准（对标互联网大厂性能基线）。
 *
 * <p>在同一环境下对比 RemiJson vs Jackson vs Fastjson2，
 * 覆盖简单 Bean、嵌套 Bean、Map/List 解析、格式化输出等场景。</p>
 *
 * <p>注：Gson / Hutool-JSON / org.json / json-simple 被 ArchUnit R29 + Enforcer BannedDependencies
 * 全局禁止直接声明，故竞品对比基准仅对比 Jackson（optional）和 Fastjson2（独立 groupId 不受限）。</p>
 *
 * <p>运行方式：</p>
 * <pre>
 *   mvn test-compile
 *   mvn exec:java -Dexec.mainClass="com.remisoft.common.json.JsonCompatBenchmark" -Dexec.classpathScope=test
 * </pre>
 *
 * @since 1.1.0
 */
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 3)
@Fork(value = 2, jvmArgsAppend = {"-Xms512m", "-Xmx512m"})
@BenchmarkMode({Mode.AverageTime, Mode.Throughput})
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
public class JsonCompatBenchmark {

    // ==================== 测试用 POJO ====================

    @SuppressWarnings("unused")
    public static class SimpleBean {
        private int id;
        private String name;
        private double score;
        private boolean active;
        private long createdAt;

        public SimpleBean() {}
        public SimpleBean(int id, String name, double score, boolean active, long createdAt) {
            this.id = id;
            this.name = name;
            this.score = score;
            this.active = active;
            this.createdAt = createdAt;
        }

        public int getId() { return id; }
        public void setId(int id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public double getScore() { return score; }
        public void setScore(double score) { this.score = score; }
        public boolean isActive() { return active; }
        public void setActive(boolean active) { this.active = active; }
        public long getCreatedAt() { return createdAt; }
        public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
    }

    // ==================== Benchmark 状态 ====================

    private ObjectMapper jacksonMapper;
    private ObjectMapper jacksonPrettyMapper;

    private SimpleBean simpleBean;
    private String simpleJson;
    private List<SimpleBean> beanList;
    private Map<String, Object> mapData;
    private String mapJson;

    @Setup(Level.Trial)
    public void setUp() {
        // Jackson 初始化
        jacksonMapper = new ObjectMapper();
        jacksonMapper.registerModule(new JavaTimeModule());
        jacksonMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);

        jacksonPrettyMapper = new ObjectMapper();
        jacksonPrettyMapper.registerModule(new JavaTimeModule());
        jacksonPrettyMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        jacksonPrettyMapper.enable(SerializationFeature.INDENT_OUTPUT);

        // 准备测试数据
        simpleBean = new SimpleBean(42, "Alice", 3.14159, true, 1700000000000L);
        simpleJson = "{\"id\":42,\"name\":\"Alice\",\"score\":3.14159,\"active\":true,\"createdAt\":1700000000000}";

        beanList = new ArrayList<>(100);
        for (int i = 0; i < 100; i++) {
            beanList.add(new SimpleBean(i, "user_" + i, i * 1.1, i % 2 == 0, 1700000000000L + i));
        }

        mapData = new LinkedHashMap<>();
        mapData.put("code", 0);
        mapData.put("message", "success");
        mapData.put("total", new BigDecimal("99.5"));
        mapData.put("items", beanList.subList(0, 10));
        mapJson = RemiJson.toJson(mapData);

        // 预热所有引擎（填充缓存、JIT 内联）
        for (int i = 0; i < 5; i++) {
            RemiJson.toJson(simpleBean);
            try {
                jacksonMapper.writeValueAsString(simpleBean);
            } catch (Exception ignored) {}
            JSON.toJSONString(simpleBean);
        }

        System.out.println("===== 跨库基准测试环境 =====");
        System.out.println("RemiJson  : ASM=" + RemiJson.isAsmAvailable() + ", nativeImage=" + RemiJson.isNativeImage());
        System.out.println("Jackson   : " + jacksonMapper.version());
        System.out.println("Fastjson2 : " + JSON.VERSION);
        System.out.println("==============================");
    }

    // ==================== 简单 Bean 序列化 ====================

    @Benchmark
    public void remiJson_serializeSimple(Blackhole bh) {
        bh.consume(RemiJson.toJson(simpleBean));
    }

    @Benchmark
    public void jackson_serializeSimple(Blackhole bh) throws Exception {
        bh.consume(jacksonMapper.writeValueAsString(simpleBean));
    }

    @Benchmark
    public void fastjson2_serializeSimple(Blackhole bh) {
        bh.consume(JSON.toJSONString(simpleBean));
    }

    // ==================== 简单 Bean 反序列化 ====================

    @Benchmark
    public void remiJson_deserializeSimple(Blackhole bh) {
        bh.consume(RemiJson.toObject(simpleJson, SimpleBean.class));
    }

    @Benchmark
    public void jackson_deserializeSimple(Blackhole bh) throws Exception {
        bh.consume(jacksonMapper.readValue(simpleJson, SimpleBean.class));
    }

    @Benchmark
    public void fastjson2_deserializeSimple(Blackhole bh) {
        bh.consume(JSON.parseObject(simpleJson, SimpleBean.class));
    }

    // ==================== List 序列化（100 条） ====================

    @Benchmark
    public void remiJson_serializeList(Blackhole bh) {
        bh.consume(RemiJson.toJson(beanList));
    }

    @Benchmark
    public void jackson_serializeList(Blackhole bh) throws Exception {
        bh.consume(jacksonMapper.writeValueAsString(beanList));
    }

    @Benchmark
    public void fastjson2_serializeList(Blackhole bh) {
        bh.consume(JSON.toJSONString(beanList));
    }

    // ==================== Map 反序列化 ====================

    @Benchmark
    public void remiJson_deserializeMap(Blackhole bh) {
        bh.consume(RemiJson.parseMap(mapJson));
    }

    @Benchmark
    public void jackson_deserializeMap(Blackhole bh) throws Exception {
        bh.consume(jacksonMapper.readValue(mapJson, Map.class));
    }

    @Benchmark
    public void fastjson2_deserializeMap(Blackhole bh) {
        bh.consume(JSON.parseObject(mapJson, Map.class));
    }

    // ==================== 格式化输出 ====================

    @Benchmark
    public void remiJson_serializePretty(Blackhole bh) {
        bh.consume(RemiJson.format(simpleBean));
    }

    @Benchmark
    public void jackson_serializePretty(Blackhole bh) throws Exception {
        bh.consume(jacksonPrettyMapper.writeValueAsString(simpleBean));
    }

    // ==================== byte[] 直接写入测试 ====================

    @Benchmark
    public void remiJson_serializeToBytes(Blackhole bh) {
        bh.consume(RemiJson.toJsonBytes(simpleBean));
    }

    @Benchmark
    public void jackson_serializeToBytes(Blackhole bh) throws Exception {
        bh.consume(jacksonMapper.writeValueAsBytes(simpleBean));
    }

    @Benchmark
    public void fastjson2_serializeToBytes(Blackhole bh) {
        bh.consume(JSON.toJSONBytes(simpleBean));
    }

    // ==================== main 方法 ====================

    public static void main(String[] args) throws Exception {
        org.openjdk.jmh.Main.main(args);
    }
}
