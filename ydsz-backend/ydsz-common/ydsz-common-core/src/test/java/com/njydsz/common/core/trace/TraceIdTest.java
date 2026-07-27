package com.njydsz.common.core.trace;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import org.assertj.core.api.Assertions;
/**
 * {@link TraceIdGenerator} 和 {@link SnowflakeTraceIdSupplier} 单元测试。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@DisplayName("TraceId 生成测试")
class TraceIdTest {

    @AfterEach
    void resetSupplier() {
        TraceIdGenerator.resetToDefault();
    }

    @Nested
    @DisplayName("TraceIdGenerator (默认 UUID 策略)")
    class TraceIdGeneratorTest {

        @Test
        @DisplayName("生成 32 位无连字符的 hex 字符串")
        void generate_length32() {
            String traceId = TraceIdGenerator.generate();
            assertThat(traceId).hasSize(32);
            assertThat(traceId).matches("^[0-9a-f]{32}$");
        }

        @Test
        @DisplayName("连续生成 1000 次无重复")
        void generate_unique() {
            Set<String> ids = new HashSet<>();
            for (int i = 0; i < 1000; i++) {
                ids.add(TraceIdGenerator.generate());
            }
            assertThat(ids).hasSize(1000);
        }

        @Test
        @DisplayName("setSupplier 注入后 generate 使用新策略")
        void setSupplier_delegates() {
            TraceIdGenerator.setSupplier(() -> "fixed-test-id");
            assertThat(TraceIdGenerator.generate()).isEqualTo("fixed-test-id");
        }

        @Test
        @DisplayName("setSupplier(null) 恢复默认 UUID 策略")
        void setSupplier_null_resetsToDefault() {
            TraceIdGenerator.setSupplier(() -> "temp-id");
            TraceIdGenerator.setSupplier(null);
            String traceId = TraceIdGenerator.generate();
            assertThat(traceId).hasSize(32);
            assertThat(traceId).matches("^[0-9a-f]{32}$");
        }

        @Test
        @DisplayName("setSupplier 注入 Snowflake 后生成 16 位有序 ID")
        void setSupplier_snowflake() {
            SnowflakeTraceIdSupplier snowflake = new SnowflakeTraceIdSupplier(1, 1);
            TraceIdGenerator.setSupplier(snowflake);
            String traceId = TraceIdGenerator.generate();
            assertThat(traceId).hasSize(16);
            assertThat(traceId).matches("^[0-9a-f]{16}$");
        }
    }

    @Nested
    @DisplayName("SnowflakeTraceIdSupplier")
    class SnowflakeTraceIdSupplierTest {

        @Test
        @DisplayName("生成 16 位十六进制字符串")
        void generate_length16() {
            SnowflakeTraceIdSupplier supplier = new SnowflakeTraceIdSupplier(1, 1);
            String traceId = supplier.generate();
            assertThat(traceId).hasSize(16);
            assertThat(traceId).matches("^[0-9a-f]{16}$");
        }

        @Test
        @DisplayName("连续生成的 ID 按时间有序")
        void generate_ordered() throws InterruptedException {
            SnowflakeTraceIdSupplier supplier = new SnowflakeTraceIdSupplier(1, 1);
            String id1 = supplier.generate();
            Thread.sleep(2);
            String id2 = supplier.generate();
            assertThat(id2).isGreaterThan(id1);
        }

        @Test
        @DisplayName("同一毫秒内序列号递增")
        void generate_sameMillisSequence() {
            SnowflakeTraceIdSupplier supplier = new SnowflakeTraceIdSupplier(1, 1);
            Set<String> ids = new HashSet<>();
            for (int i = 0; i < 100; i++) {
                ids.add(supplier.generate());
            }
            assertThat(ids).hasSize(100);
        }

        @Test
        @DisplayName("并发 10 线程各 100 次生成无重复")
        void generate_concurrent() throws InterruptedException {
            SnowflakeTraceIdSupplier supplier = new SnowflakeTraceIdSupplier(1, 1);
            int threadCount = 10;
            int perThread = 100;
            ExecutorService pool = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);
            Set<String> ids = new HashSet<>();
            AtomicInteger duplicates = new AtomicInteger(0);

            for (int t = 0; t < threadCount; t++) {
                pool.submit(() -> {
                    try {
                        for (int i = 0; i < perThread; i++) {
                            String id = supplier.generate();
                            synchronized (ids) {
                                if (!ids.add(id)) {
                                    duplicates.incrementAndGet();
                                }
                            }
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }

            latch.await();
            pool.shutdown();

            assertThat(duplicates.get()).isZero();
            assertThat(ids).hasSize(threadCount * perThread);
        }

        @Test
        @DisplayName("自动推导 workerId 和 datacenterId")
        void generate_autoDerived() {
            SnowflakeTraceIdSupplier supplier = new SnowflakeTraceIdSupplier();
            String traceId = supplier.generate();
            assertThat(traceId).hasSize(16);
            assertThat(traceId).matches("^[0-9a-f]{16}$");
        }

        @Test
        @DisplayName("无效 datacenterId 抛出异常")
        void invalidDatacenterId() {
            Assertions.assertThatThrownBy(
                    () -> new SnowflakeTraceIdSupplier(100, 0))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
