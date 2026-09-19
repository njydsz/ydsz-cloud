package com.njydsz.common.json.internal;

import java.util.concurrent.atomic.AtomicLong;

/**
 * JSON 引擎运行时指标计数器（P2-D3 JMX 可观测性支撑）。
 *
 * <p>记录序列化/反序列化的累计调用次数与耗时（纳秒），供 {@link
 * com.njydsz.common.json.spring.boot.JsonConfigViewerMBean} 通过 JMX 暴露。
 *
 * <p>计数器采用 {@link AtomicLong}，写入路径仅涉及单次 {@code atomic.incrementAndGet()} 与
 * {@code System.nanoTime()} 差值加法，对热路径性能影响可忽略不计。读取路径（JMX 轮询）无写争用。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public final class JsonMetrics {

  private JsonMetrics() {}

  private static final AtomicLong SERIALIZE_COUNT = new AtomicLong(0);
  private static final AtomicLong SERIALIZE_TIME_NANOS = new AtomicLong(0);
  private static final AtomicLong DESERIALIZE_COUNT = new AtomicLong(0);
  private static final AtomicLong DESERIALIZE_TIME_NANOS = new AtomicLong(0);

  /**
   * 记录一次序列化调用的耗时。
   *
   * @param elapsedNanos 调用耗时（纳秒）
   */
  public static void recordSerialize(long elapsedNanos) {
    SERIALIZE_COUNT.incrementAndGet();
    SERIALIZE_TIME_NANOS.addAndGet(elapsedNanos);
  }

  /**
   * 记录一次反序列化调用的耗时。
   *
   * @param elapsedNanos 调用耗时（纳秒）
   */
  public static void recordDeserialize(long elapsedNanos) {
    DESERIALIZE_COUNT.incrementAndGet();
    DESERIALIZE_TIME_NANOS.addAndGet(elapsedNanos);
  }

  public static long getSerializeCount() {
    return SERIALIZE_COUNT.get();
  }

  public static long getSerializeTimeNanos() {
    return SERIALIZE_TIME_NANOS.get();
  }

  public static long getDeserializeCount() {
    return DESERIALIZE_COUNT.get();
  }

  public static long getDeserializeTimeNanos() {
    return DESERIALIZE_TIME_NANOS.get();
  }
}
