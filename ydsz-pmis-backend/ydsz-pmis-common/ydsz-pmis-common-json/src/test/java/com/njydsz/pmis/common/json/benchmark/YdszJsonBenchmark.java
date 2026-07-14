package com.njydsz.pmis.common.json.benchmark;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import com.njydsz.pmis.common.json.YdszJson;

/**
 * YdszJson JMH 基准测试套件。
 *
 * <p>用于验证性能优化效果和检测性能回归。</p>
 *
 * <p>运行方式：</p>
 * <pre>
 * mvn test -Dtest=YdszJsonBenchmark
 * # 或直接运行
 * java -jar target/benchmarks.jar
 * </pre>
 *
 * @author ydsz-pmis-team
 * @since 1.4.0
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@State(Scope.Benchmark)
public class YdszJsonBenchmark {

    private TestBean simpleBean;
    private List<TestBean> beanList;
    private Map<String, Object> complexMap;
    private String simpleJson;
    private String listJson;
    private String complexJson;

    @Setup
    public void setup() {
        simpleBean = new TestBean("Alice", 30, true, 3.14);

        beanList = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            beanList.add(new TestBean("User" + i, i * 10, i % 2 == 0, i * 1.5));
        }

        complexMap = new HashMap<>();
        complexMap.put("name", "Project Alpha");
        complexMap.put("count", 1000);
        complexMap.put("active", true);
        Map<String, Object> nested = new HashMap<>();
        nested.put("key", "value");
        nested.put("num", 42);
        complexMap.put("nested", nested);

        simpleJson = YdszJson.toJson(simpleBean);
        listJson = YdszJson.toJson(beanList);
        complexJson = YdszJson.toJson(complexMap);
    }

    @Benchmark
    public String serializeBean() {
        return YdszJson.toJson(simpleBean);
    }

    @Benchmark
    public String serializeList() {
        return YdszJson.toJson(beanList);
    }

    @Benchmark
    public String serializeMap() {
        return YdszJson.toJson(complexMap);
    }

    @Benchmark
    public byte[] serializeToBytes() {
        return YdszJson.toJsonBytes(simpleBean);
    }

    @Benchmark
    public TestBean deserializeBean() {
        return YdszJson.toObject(simpleJson, TestBean.class);
    }

    @Benchmark
    public Map<String, Object> deserializeMap() {
        return YdszJson.parseMap(complexJson);
    }

    @Benchmark
    public List<Object> deserializeArray() {
        return YdszJson.parseArray(listJson);
    }

    /**
     * 测试用的简单 Bean。
     */
    public static class TestBean {
        private String name;
        private int age;
        private boolean active;
        private double score;

        public TestBean() {
        }

        public TestBean(String name, int age, boolean active, double score) {
            this.name = name;
            this.age = age;
            this.active = active;
            this.score = score;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getAge() {
            return age;
        }

        public void setAge(int age) {
            this.age = age;
        }

        public boolean isActive() {
            return active;
        }

        public void setActive(boolean active) {
            this.active = active;
        }

        public double getScore() {
            return score;
        }

        public void setScore(double score) {
            this.score = score;
        }
    }
}
