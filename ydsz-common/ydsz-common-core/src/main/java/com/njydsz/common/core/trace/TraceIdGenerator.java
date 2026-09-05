package com.njydsz.common.core.trace;

import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 * TraceId 生成器（基于 ThreadLocalRandom / SecureRandom + HexFormat）。
 *
 * <p>提供两套实现，适用于不同场景：
 *
 * <ul>
 *   <li>内部高性能路径：使用 {@link ThreadLocalRandom}（线程本地伪随机数，无锁竞争），经 {@link HexFormat} 格式化为小写十六进制字符串
 *   <li>W3C 跨组织边界路径：使用 {@link SecureRandom}（密码学级熵源），满足 W3C Trace Context 标准要求
 * </ul>
 *
 * <p>设计采用纯函数式风格：每次调用直接分配 byte 数组。在现代 JVM（ZGC/Shenandoah）下，16 字节的 TLAB 分配几乎零成本，无需 ThreadLocal
 * 缓冲区增加的复杂度和生命周期管理负担。
 *
 * <h3>性能说明</h3>
 *
 * <ul>
 *   <li>{@link #generateSortableTraceId()} 按时间排序，使用全局原子计数器 +
 *       同步块保证同毫秒内序号单调；同毫秒内不同线程的序号保序；锁粒度仅覆盖序号递增，适合绝大多数场景
 *   <li>{@link #generateW3CTraceId()} / {@link #generateW3CSpanId()} 使用 {@link SecureRandom}，
 *       密码学级安全但吞吐量略低，适用于跨组织 W3C 传播场景
 * </ul>
 *
 * <h3>安全性说明</h3>
 *
 * <p>TraceId 用于日志关联和链路追踪，非密码学用途。两种实现碰撞概率均约 2^-128。
 *
 * <p><b>线程安全：</b>{@link ThreadLocalRandom} 线程本地天然安全；{@link SecureRandom} 内部同步，多线程共享无竞争风险；可排序方案通过全局
 * 同步块保证同毫秒序号唯一。
 *
 * <h3>W3C TraceContext 支持</h3>
 *
 * <p>提供符合 W3C Trace Context 标准的 spanId 生成和 traceparent header 构建方法，便于对接 SkyWalking/Jaeger/Zipkin
 * 等主流链路追踪系统。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public final class TraceIdGenerator {

  /** traceId 字节长度（16 bytes = 128 bit = 32 hex 字符） */
  private static final int TRACE_ID_BYTES = 16;

  /** spanId 字节长度（8 bytes = 64 bit = 16 hex 字符） */
  private static final int SPAN_ID_BYTES = 8;

  /** 共享的 HexFormat 实例（线程安全，可重用）。 */
  private static final HexFormat HEX_FORMAT = HexFormat.of();

  /** 密码学安全随机数生成器（用于 W3C Trace Context 跨组织边界传播场景）。 */
  private static final SecureRandom SECURE_RANDOM = new SecureRandom();

  /** 同一毫秒内的序号上限（14 bit，0~16383），允许单线程每毫秒最多 16384 次调用。超出后进入下一毫秒重新计数。 */
  private static final int MAX_SEQ_PER_MS = 0x3FFF;

  /**
   * 上一毫秒时间戳（全局）。
   *
   * <p>使用 AtomicLong 保证内存可见性，结合同步块保证序号分配的原子性。替代早期 ThreadLocal 方案以遵循 YDIZ-CONC-002 规范。
   */
  private static final AtomicLong LAST_MILLIS = new AtomicLong(0L);

  /**
   * 同毫秒内的全局序号计数器。
   *
   * <p>在同步块内递增，保证同一毫秒内所有线程分配到的序号唯一且单调。跨毫秒自动归零。
   */
  private static final AtomicLong SEQ_COUNTER = new AtomicLong(0L);

  /** 用于保护毫秒进位与序号归零的原子性（锁粒度极小，仅覆盖序号递增）。 */
  private static final Object SEQ_LOCK = new Object();

  private TraceIdGenerator() {
    throw new UnsupportedOperationException("Utility class");
  }

  /**
   * 生成 32 位十六进制 TraceId。
   *
   * <p>默认使用可排序版本（{@link #generateSortableTraceId()}），按时间有序，适合日志关联和链路追踪场景。
   *
   * @return 32 位小写十六进制字符串
   * @since 26.09.01
   */
  public static String generateTraceId() {
    return generateSortableTraceId();
  }

  /**
   * 生成按时间有序（可排序）的 32 位十六进制 TraceId（UUIDv7 风格）。
   *
   * <p>布局（16 bytes / 32 hex）：
   *
   * <ul>
   *   <li>bytes[0..5]（48 bit）：大端毫秒时间戳
   *   <li>bytes[6..7]（14 bit）：同一毫秒内的单调序号，保证同毫秒内字典序严格递增
   *   <li>bytes[8..15]（66 bit）：随机数，保证唯一性与分布均匀性
   * </ul>
   *
   * <p>本方法生成的 id 可直接用于日志 / 链路存储的按时间排序与范围检索，便于问题排查。输出格式为 32 位小写 hex，与旧版纯随机 TraceId 格式一致，下游无需感知差异。
   *
   * <p>本方法使用全局原子计数器 + 轻量级同步块维护时间戳+序号状态，无锁时间占绝大部分，适合高并发场景下生成有序 traceId。
   *
   * @return 32 位小写十六进制字符串（时间有序）
   * @since 26.09.01
   */
  public static String generateSortableTraceId() {
    byte[] bytes = new byte[TRACE_ID_BYTES];
    long timeMillis = System.currentTimeMillis();
    long seq = nextGlobalSeq(timeMillis);

    // 48-bit 大端时间戳
    bytes[0] = (byte) (timeMillis >>> 40);
    bytes[1] = (byte) (timeMillis >>> 32);
    bytes[2] = (byte) (timeMillis >>> 24);
    bytes[3] = (byte) (timeMillis >>> 16);
    bytes[4] = (byte) (timeMillis >>> 8);
    bytes[5] = (byte) timeMillis;
    // 14-bit 序号（大端），高位留 2 bit 作为 00 前缀，填充到一个 short（2 bytes）
    // 布局：bytes[6] = 00 + seq高6bit; bytes[7] = seq低8bit
    bytes[6] = (byte) ((seq & 0x3F00) >>> 8);
    bytes[7] = (byte) (seq & 0xFF);
    // 剩余 64-bit 随机数（8 bytes）
    byte[] rand = new byte[TRACE_ID_BYTES - 8];
    ThreadLocalRandom.current().nextBytes(rand);
    System.arraycopy(rand, 0, bytes, 8, rand.length);
    return HEX_FORMAT.formatHex(bytes);
  }

  /**
   * 获取下一个全局序号。
   *
   * <p>使用轻量级同步块保证时间戳进位与序号归零的原子性。锁内操作极少（<5 条字节码），对高并发影响极低。
   *
   * @param nowMillis 当前毫秒时间戳
   * @return 当前使用的序号
   */
  private static long nextGlobalSeq(long nowMillis) {
    synchronized (SEQ_LOCK) {
      long last = LAST_MILLIS.get();
      if (nowMillis != last) {
        LAST_MILLIS.set(nowMillis);
        SEQ_COUNTER.set(0L);
      }
      return SEQ_COUNTER.getAndIncrement() & MAX_SEQ_PER_MS;
    }
  }

  /**
   * 生成 16 位十六进制 SpanId（8 bytes 随机数）。
   *
   * <p>SpanId 用于标识一次分布式调用中的单个操作，符合 W3C Trace Context 规范。
   *
   * @return 16 位十六进制字符串
   * @since 26.09.01
   */
  public static String generateSpanId() {
    byte[] bytes = new byte[SPAN_ID_BYTES];
    ThreadLocalRandom.current().nextBytes(bytes);
    return HEX_FORMAT.formatHex(bytes);
  }

  /**
   * 生成符合 W3C Trace Context 标准的 traceparent header 值。
   *
   * <p>格式：{@code 00-{traceId}-{spanId}-{traceFlags}}，其中：
   *
   * <ul>
   *   <li>{@code version} = {@code 00}（W3C 当前版本）
   *   <li>{@code traceId} = 32 位十六进制
   *   <li>{@code spanId} = 16 位十六进制
   *   <li>{@code traceFlags} = {@code 01}（sampled，已采样）
   * </ul>
   *
   * <p>直接赋值给 HTTP header {@code traceparent} 即可对接 SkyWalking/Jaeger/Zipkin。
   *
   * @return W3C traceparent header 值
   * @since 26.09.01
   * @see <a href="https://www.w3.org/TR/trace-context/">W3C Trace Context</a>
   */
  public static String traceparentHeader() {
    return "00-" + generateSortableTraceId() + "-" + generateSpanId() + "-01";
  }

  /**
   * 以指定的 traceId 和 spanId 构建 W3C traceparent header 值。
   *
   * <p>用于跨服务传播已有 traceId 时构建下游请求的 traceparent。
   *
   * @param traceId 上游传入的 traceId（32 位十六进制）
   * @param spanId 当前服务生成的 spanId（16 位十六进制）
   * @return W3C traceparent header 值
   * @since 26.09.01
   */
  public static String traceparentHeader(String traceId, String spanId) {
    return "00-" + traceId + "-" + spanId + "-01";
  }

  /**
   * 解析 W3C Trace Context traceparent header 值。
   *
   * <p>格式：{@code {version}-{traceId}-{spanId}-{traceFlags}}，如 {@code
   * 00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01}。
   *
   * @param traceparent W3C traceparent 字符串，非空
   * @return 解析结果；格式非法时返回 null
   * @since 26.09.01
   * @see <a href="https://www.w3.org/TR/trace-context/">W3C Trace Context</a>
   */
  public static ParsedTraceparent parseTraceparent(String traceparent) {
    if (traceparent == null || traceparent.isEmpty()) {
      return null;
    }
    String[] parts = traceparent.split("-");
    if (parts.length != 4 || parts[1].length() != 32 || parts[2].length() != 16) {
      return null;
    }
    try {
      int version = Integer.parseInt(parts[0], 16);
      int traceFlags = Integer.parseInt(parts[3], 16);
      return new ParsedTraceparent(version, parts[1], parts[2], traceFlags);
    } catch (NumberFormatException e) {
      return null;
    }
  }

  /**
   * 解析后的 W3C traceparent 组件。
   *
   * @param version 版本（通常为 {@code 00}）
   * @param traceId 32 位十六进制 traceId
   * @param spanId 16 位十六进制 spanId（注入后可作为 parentSpanId）
   * @param traceFlags 2 位十六进制 traceFlags（{@code 01} = sampled）
   * @since 26.09.01
   * @see <a href="https://www.w3.org/TR/trace-context/">W3C Trace Context</a>
   */
  public record ParsedTraceparent(int version, String traceId, String spanId, int traceFlags) {}

  /**
   * 生成符合 W3C Trace Context 标准的 32 位十六进制 TraceId。
   *
   * <p>使用 {@link SecureRandom} 生成 128 bit 密码学安全随机数，格式化为 32 位小写 hex。适用于 W3C Trace Context
   * 跨组织边界传播场景，提供最高级别的唯一性保障（碰撞概率约 2^-128）。
   *
   * <p>与 {@link #generateSortableTraceId()} 的区别：
   *
   * <ul>
   *   <li>W3C 版使用 {@link SecureRandom}（密码学熵源，稍慢但更安全）
   *   <li>可排序版使用 {@link ThreadLocalRandom}（高性能，按时间有序）
   * </ul>
   *
   * @return 32 位小写十六进制字符串
   * @since 26.09.01
   * @see <a href="https://www.w3.org/TR/trace-context/">W3C Trace Context</a>
   */
  public static String generateW3CTraceId() {
    byte[] bytes = new byte[TRACE_ID_BYTES];
    SECURE_RANDOM.nextBytes(bytes);
    return HEX_FORMAT.formatHex(bytes);
  }

  /**
   * 生成符合 W3C Trace Context 标准的 16 位十六进制 SpanId。
   *
   * <p>使用 {@link SecureRandom} 生成 64 bit 密码学安全随机数，格式化为 16 位小写 hex。与 {@link #generateW3CTraceId()}
   * 配套使用，用于 W3C Trace Context 标准场景。
   *
   * @return 16 位小写十六进制字符串
   * @since 26.09.01
   * @see <a href="https://www.w3.org/TR/trace-context/">W3C Trace Context</a>
   */
  public static String generateW3CSpanId() {
    byte[] bytes = new byte[SPAN_ID_BYTES];
    SECURE_RANDOM.nextBytes(bytes);
    return HEX_FORMAT.formatHex(bytes);
  }
}
