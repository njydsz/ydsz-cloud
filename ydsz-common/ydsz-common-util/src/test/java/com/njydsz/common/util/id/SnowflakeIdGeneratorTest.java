package com.njydsz.common.util.id;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SnowflakeIdGenerator 核心算法测试。
 *
 * <p>覆盖四类不变量：
 *
 * <ol>
 *   <li>ID 结构往返：workerId/datacenterId/timestamp/sequence 反解与构造参数一致
 *   <li>并发唯一性：多线程批量生成无重复
 *   <li>时钟回拨分级处理：≤5ms 等待恢复、&gt;5ms 抛 {@link ClockBackwardException}
 *   <li>参数校验：sequenceBits / workerId 边界
 * </ol>
 *
 * <p>时钟回拨路径通过包级测试构造器注入 {@link MutableClock} 受控时间源验证， 不依赖真实系统时钟。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
class SnowflakeIdGeneratorTest {

  /** 测试用起始纪元（2020-01-01 UTC） */
  private static final long TEST_EPOCH = 1577836800000L;

  /** 并发测试：线程数 */
  private static final int CONCURRENCY = 16;

  /** 并发测试：每线程生成 ID 数 */
  private static final int IDS_PER_THREAD = 2000;

  /** 时钟回拨容忍阈值内（≤5ms）的回拨量 */
  private static final long SMALL_BACKWARD_MS = 3L;

  /** 时钟回拨容忍阈值外（>5ms）的回拨量 */
  private static final long LARGE_BACKWARD_MS = 10L;

  /** 时钟追平等待时长（毫秒） */
  private static final long CLOCK_RECOVER_DELAY_MS = 100L;

  /** 可变时间源：支持测试中手动推进/回拨时钟。 */
  private static final class MutableClock implements LongSupplier {

    private final AtomicLong current;

    MutableClock(long initialMillis) {
      this.current = new AtomicLong(initialMillis);
    }

    void set(long millis) {
      current.set(millis);
    }

    @Override
    public long getAsLong() {
      return current.get();
    }
  }

  /**
   * 构造显式指定 workerId/datacenterId/epoch 的配置（绕开环境探测，保证测试确定性）。
   *
   * @param epoch 起始纪元
   * @param workerId 工作节点 ID
   * @param datacenterId 数据中心 ID
   * @return SnowflakeProperties 实例
   */
  private SnowflakeProperties properties(long epoch, long workerId, long datacenterId) {
    SnowflakeProperties props = new SnowflakeProperties();
    props.setEpoch(epoch);
    props.setWorkerId(workerId);
    props.setDatacenterId(datacenterId);
    return props;
  }

  @Test
  @DisplayName("ID 结构往返：反解出的 workerId/datacenterId/timestamp/sequence 与构造参数一致")
  void idStructureRoundTrip() {
    long absoluteTime = TEST_EPOCH + 12345L;
    MutableClock clock = new MutableClock(absoluteTime);
    SnowflakeIdGenerator generator =
        new SnowflakeIdGenerator(
            properties(TEST_EPOCH, 7L, 3L),
            WorkerIdAllocatorChain.defaults(),
            SnowflakeIdGenerator.DEFAULT_SEQUENCE_BITS,
            clock);

    long id = generator.nextId();

    assertThat(generator.parseWorkerId(id)).isEqualTo(7L);
    assertThat(generator.parseDatacenterId(id)).isEqualTo(3L);
    assertThat(generator.parseTimestamp(id)).isEqualTo(absoluteTime);
    assertThat(generator.parseSequence(id)).isZero();
  }

  @Test
  @DisplayName("单线程连续生成：同一毫秒内 ID 严格单调递增（13-bit 序列号无溢出）")
  void sequentialIdsMonotonicWithinSameMillis() {
    MutableClock clock = new MutableClock(TEST_EPOCH + 1000L);
    SnowflakeIdGenerator generator =
        new SnowflakeIdGenerator(
            properties(TEST_EPOCH, 1L, 1L),
            WorkerIdAllocatorChain.defaults(),
            SnowflakeIdGenerator.MAX_SEQUENCE_BITS,
            clock);

    long previous = 0L;
    for (int i = 0; i < 1000; i++) {
      long id = generator.nextId();
      assertThat(id).as("第 %d 个 ID 应大于前一个", i).isGreaterThan(previous);
      previous = id;
    }
  }

  @Test
  @DisplayName("并发唯一性：16 线程 × 2000 个 ID 全局无重复")
  void concurrentGenerationUniqueness() throws InterruptedException {
    SnowflakeIdGenerator generator =
        new SnowflakeIdGenerator(
            properties(TEST_EPOCH, 1L, 1L),
            WorkerIdAllocatorChain.defaults(),
            SnowflakeIdGenerator.MAX_SEQUENCE_BITS);
    Set<Long> generated = ConcurrentHashMap.newKeySet(CONCURRENCY * IDS_PER_THREAD);
    CountDownLatch ready = new CountDownLatch(CONCURRENCY);
    CountDownLatch start = new CountDownLatch(1);
    CountDownLatch done = new CountDownLatch(CONCURRENCY);

    for (int i = 0; i < CONCURRENCY; i++) {
      Thread worker =
          new Thread(
              () -> {
                ready.countDown();
                try {
                  start.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                  return;
                }
                for (int j = 0; j < IDS_PER_THREAD; j++) {
                  generated.add(generator.nextId());
                }
                done.countDown();
              },
              "snowflake-test-worker-" + i);
      worker.start();
    }

    assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
    start.countDown();
    assertThat(done.await(60, TimeUnit.SECONDS))
        .as("并发 ID 生成应在 60s 内完成").isTrue();

    assertThat(generated)
        .as("32,000 个并发生成的 ID 必须全局唯一")
        .hasSize(CONCURRENCY * IDS_PER_THREAD);
  }

  @Test
  @DisplayName("时钟回拨 ≤5ms：等待时钟追平后恢复生成，不抛异常")
  void smallClockBackwardWaitsForRecovery() throws InterruptedException {
    MutableClock clock = new MutableClock(TEST_EPOCH + 1000L);
    SnowflakeIdGenerator generator =
        new SnowflakeIdGenerator(
            properties(TEST_EPOCH, 1L, 1L),
            WorkerIdAllocatorChain.defaults(),
            SnowflakeIdGenerator.DEFAULT_SEQUENCE_BITS,
            clock);

    long first = generator.nextId();

    // 时钟回拨 3ms（阈值内），另一线程在 100ms 后将时钟推过上次时间戳
    clock.set(TEST_EPOCH + 1000L - SMALL_BACKWARD_MS);
    Thread clockRecoverer =
        new Thread(
            () -> {
              try {
                Thread.sleep(CLOCK_RECOVER_DELAY_MS);
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              }
              clock.set(TEST_EPOCH + 1000L + CLOCK_RECOVER_DELAY_MS);
            },
            "snowflake-clock-recoverer");
    clockRecoverer.start();

    long second = generator.nextId();
    clockRecoverer.join(5000L);

    assertThat(second).as("恢复后生成的 ID 应晚于回拨前的 ID").isGreaterThan(first);
  }

  @Test
  @DisplayName("时钟回拨 >5ms：立即抛出 ClockBackwardException")
  void largeClockBackwardThrowsImmediately() {
    MutableClock clock = new MutableClock(TEST_EPOCH + 1000L);
    SnowflakeIdGenerator generator =
        new SnowflakeIdGenerator(
            properties(TEST_EPOCH, 1L, 1L),
            WorkerIdAllocatorChain.defaults(),
            SnowflakeIdGenerator.DEFAULT_SEQUENCE_BITS,
            clock);

    generator.nextId();
    clock.set(TEST_EPOCH + 1000L - LARGE_BACKWARD_MS);

    assertThatThrownBy(generator::nextId).isInstanceOf(ClockBackwardException.class);
  }

  @Test
  @DisplayName("sequenceBits 边界校验：0 与超上限均拒绝")
  void sequenceBitsValidation() {
    SnowflakeProperties props = properties(TEST_EPOCH, 1L, 1L);

    assertThatThrownBy(
            () ->
                new SnowflakeIdGenerator(
                    props, WorkerIdAllocatorChain.defaults(), 0, new MutableClock(TEST_EPOCH)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("sequenceBits");

    assertThatThrownBy(
            () ->
                new SnowflakeIdGenerator(
                    props,
                    WorkerIdAllocatorChain.defaults(),
                    SnowflakeIdGenerator.MAX_SEQUENCE_BITS + 1,
                    new MutableClock(TEST_EPOCH)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("sequenceBits");
  }

  @Test
  @DisplayName("workerId 边界校验：超出 1023 拒绝")
  void workerIdValidation() {
    assertThatThrownBy(
            () ->
                new SnowflakeIdGenerator(
                    properties(TEST_EPOCH, 1024L, 1L),
                    WorkerIdAllocatorChain.defaults(),
                    SnowflakeIdGenerator.DEFAULT_SEQUENCE_BITS,
                    new MutableClock(TEST_EPOCH)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("worker");
  }

  @Test
  @DisplayName("allocatorName 暴露分配策略链名称（健康检查/排障用）")
  void allocatorNameExposed() {
    SnowflakeIdGenerator generator =
        new SnowflakeIdGenerator(
            properties(TEST_EPOCH, 1L, 1L),
            WorkerIdAllocatorChain.defaults(),
            SnowflakeIdGenerator.DEFAULT_SEQUENCE_BITS,
            new MutableClock(TEST_EPOCH));

    assertThat(generator.getAllocatorName()).contains("IpHash");
  }
}
