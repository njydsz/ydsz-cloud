package com.njydsz.common.util.benchmark;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import com.njydsz.common.util.collection.CollectionUtils;
import com.njydsz.common.util.security.DigestUtils;
import com.njydsz.common.util.string.StringUtils;

/**
 * JMH 基准测试套件
 *
 * <p>对核心工具类进行性能基准测试，覆盖序列化、反序列化、字符串处理、
 * 集合操作、加密计算等高频场景。
 *
 * <p><b>运行方式：</b>
 * <pre>
 * mvn clean compile -pl ydsz-common/ydsz-common-util -am
 * java -jar target/benchmarks.jar UtilBenchmark
 * </pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(value = 1)
public class UtilBenchmark {

    private static final String TEST_STRING = "ydsz-benchmark-test-string-1234567890";
    private static final String TEST_REGEX = "\\d+";

    // ==================== DigestUtils 基准测试 ====================

    @Benchmark
    public void benchmarkMd5Hex(Blackhole bh) {
        bh.consume(DigestUtils.md5Hex(TEST_STRING));
    }

    @Benchmark
    public void benchmarkSha256Hex(Blackhole bh) {
        bh.consume(DigestUtils.sha256Hex(TEST_STRING));
    }

    @Benchmark
    public void benchmarkHmacSha256Hex(Blackhole bh) {
        bh.consume(DigestUtils.hmacSha256Hex(TEST_STRING, "benchmark-key"));
    }

    // ==================== StringUtils 基准测试 ====================

    @Benchmark
    public void benchmarkIsBlank(Blackhole bh) {
        bh.consume(StringUtils.isBlank(TEST_STRING));
    }

    @Benchmark
    public void benchmarkToCamelCase(Blackhole bh) {
        bh.consume(StringUtils.toCamelCase("user_name_id"));
    }

    @Benchmark
    public void benchmarkIsMatch(Blackhole bh) {
        bh.consume(StringUtils.isMatch(TEST_STRING, TEST_REGEX));
    }

    // ==================== CollectionUtils 基准测试 ====================

    @Benchmark
    public void benchmarkIsEmpty(Blackhole bh) {
        bh.consume(CollectionUtils.isEmpty(Arrays.asList("a", "b", "c")));
    }

    @Benchmark
    public void benchmarkCollectionIsEmpty(Blackhole bh) {
        List<String> list = new ArrayList<>();
        list.add("a");
        list.add("b");
        bh.consume(CollectionUtils.isEmpty(list));
    }
}
