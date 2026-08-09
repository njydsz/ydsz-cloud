package com.njydsz.common.core.trace;

import java.util.HexFormat;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 * TraceId 生成器（基于 ThreadLocalRandom + HexFormat）。
 *
 * <p>使用 {@link ThreadLocalRandom}（线程本地伪随机数，无锁竞争）生成随机字节，
 * 经 {@link HexFormat} 格式化为小写十六进制字符串，保证分布式环境下高概率全局唯一。</p>
 *
 * <p>设计采用纯函数式风格：每次调用直接分配 byte 数组。
 * 在现代 JVM（ZGC/Shenandoah）下，16 字节的 TLAB 分配几乎零成本，
 * 无需 ThreadLocal 缓冲区增加的复杂度和生命周期管理负担。</p>
 *
 * <h3>性能对比（JDK 21）</h3>
 * <ul>
 *   <li>UUID.randomUUID() — SecureRandom 每次获取熵，高并发瓶颈</li>
 *   <li>{@link ThreadLocalRandom} — 线程本地无锁，约 2.5x 于 UUID，零依赖</li>
 * </ul>
 *
 * <h3>安全性说明</h3>
 * <p>TraceId 用于日志关联和链路追踪，非密码学用途。{@link ThreadLocalRandom} 满足"
 * 高概率全局唯一"的要求，碰撞概率约 2^-128。</p>
 *
 * <p><b>线程安全：</b>{@link ThreadLocalRandom} 线程本地，天然线程安全。</p>
 *
 * <h3>W3C TraceContext 支持</h3>
 * <p>提供符合 W3C Trace Context 标准的 spanId 生成和 traceparent header 构建方法，
 * 便于对接 SkyWalking/Jaeger/Zipkin 等主流链路追踪系统。</p>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class TraceIdGenerator {

    private static final int TRACE_ID_BYTES = 16;
    private static final int SPAN_ID_BYTES = 8;

    /**
     * 共享的 HexFormat 实例（线程安全，可重用）。
     */
    private static final HexFormat HEX_FORMAT = HexFormat.of();

    private TraceIdGenerator() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 生成 32 位十六进制 TraceId（16 bytes 随机数）。
     *
     * <p>输出格式与旧版兼容（32 位小写十六进制字符串）。</p>
     *
     * @return 32 位十六进制字符串
     * @since 1.5.0
     */
    public static String generateTraceId() {
        byte[] bytes = new byte[TRACE_ID_BYTES];
        ThreadLocalRandom.current().nextBytes(bytes);
        return HEX_FORMAT.formatHex(bytes);
    }

    /**
     * 同一毫秒内的单调序号上限（16 bit），超过则自旋等待毫秒进位。
     */
    private static final int MAX_SEQ_PER_MS = 0xFFFF;

    /**
     * 打包 (毫秒时间戳 << 16 | 同毫秒单调序号) 的原子计数器，
     * 保证单线程内严格递增、多线程下高概率有序且不重复。
     */
    private static final AtomicLong LAST_TS_SEQ = new AtomicLong(0L);

    /**
     * 生成按时间有序（可排序）的 32 位十六进制 TraceId（UUIDv7 风格）。
     *
     * <p>布局（16 bytes / 32 hex）：</p>
     * <ul>
     *   <li>bytes[0..5]（48 bit）：大端毫秒时间戳</li>
     *   <li>bytes[6..7]（16 bit）：同一毫秒内的单调序号，保证同毫秒内字典序严格递增</li>
     *   <li>bytes[8..15]（64 bit）：随机数，保证唯一性与分布均匀性</li>
     * </ul>
     *
     * <p>相比 {@link #generateTraceId()} 的纯随机实现，本方法生成的 id 可直接用于
     * 日志 / 链路存储的按时间排序与范围检索，便于问题排查。两者输出格式相同（32 位小写 hex），
     * 可共存；默认 {@link #generateTraceId()} 仍保持随机以最大化分布均匀性。</p>
     *
     * @return 32 位小写十六进制字符串（时间有序）
     * @since 1.9.1
     */
    public static String generateSortableTraceId() {
        byte[] bytes = new byte[TRACE_ID_BYTES];
        long timeMillis = System.currentTimeMillis();
        long packed;
        long cur;
        // CAS 自旋：同毫秒内序号 +1；毫秒进位时序号归零
        while (true) {
            cur = LAST_TS_SEQ.get();
            long ts = cur >>> 16;
            long seq = cur & MAX_SEQ_PER_MS;
            long nextTs;
            long nextSeq;
            if (timeMillis > ts) {
                nextTs = timeMillis;
                nextSeq = 0L;
            } else {
                // 同一毫秒：序号自增；达到上限则等待毫秒进位
                if (seq >= MAX_SEQ_PER_MS) {
                    timeMillis = System.currentTimeMillis();
                    continue;
                }
                nextTs = ts;
                nextSeq = seq + 1;
            }
            packed = (nextTs << 16) | nextSeq;
            if (LAST_TS_SEQ.compareAndSet(cur, packed)) {
                break;
            }
        }
        long ts = packed >>> 16;
        int seq = (int) (packed & MAX_SEQ_PER_MS);
        // 48-bit 大端时间戳
        bytes[0] = (byte) (ts >>> 40);
        bytes[1] = (byte) (ts >>> 32);
        bytes[2] = (byte) (ts >>> 24);
        bytes[3] = (byte) (ts >>> 16);
        bytes[4] = (byte) (ts >>> 8);
        bytes[5] = (byte) ts;
        // 16-bit 单调序号（大端）
        bytes[6] = (byte) (seq >>> 8);
        bytes[7] = (byte) seq;
        // 剩余 64-bit 随机数
        byte[] rand = new byte[TRACE_ID_BYTES - 8];
        ThreadLocalRandom.current().nextBytes(rand);
        System.arraycopy(rand, 0, bytes, 8, rand.length);
        return HEX_FORMAT.formatHex(bytes);
    }

    /**
     * 生成 16 位十六进制 SpanId（8 bytes 随机数）。
     *
     * <p>SpanId 用于标识一次分布式调用中的单个操作，符合 W3C Trace Context 规范。</p>
     *
     * @return 16 位十六进制字符串
     * @since 1.5.0
     */
    public static String generateSpanId() {
        byte[] bytes = new byte[SPAN_ID_BYTES];
        ThreadLocalRandom.current().nextBytes(bytes);
        return HEX_FORMAT.formatHex(bytes);
    }

    /**
     * 生成符合 W3C Trace Context 标准的 traceparent header 值。
     *
     * <p>格式：{@code 00-{traceId}-{spanId}-{traceFlags}}，其中：</p>
     * <ul>
     *   <li>{@code version} = {@code 00}（W3C 当前版本）</li>
     *   <li>{@code traceId} = 32 位十六进制</li>
     *   <li>{@code spanId}  = 16 位十六进制</li>
     *   <li>{@code traceFlags} = {@code 01}（sampled，已采样）</li>
     * </ul>
     *
     * <p>直接赋值给 HTTP header {@code traceparent} 即可对接 SkyWalking/Jaeger/Zipkin。</p>
     *
     * @return W3C traceparent header 值
     * @since 1.5.0
     * @see <a href="https://www.w3.org/TR/trace-context/">W3C Trace Context</a>
     */
    public static String traceparentHeader() {
        return "00-" + generateTraceId() + "-" + generateSpanId() + "-01";
    }

    /**
     * 以指定的 traceId 和 spanId 构建 W3C traceparent header 值。
     *
     * <p>用于跨服务传播已有 traceId 时构建下游请求的 traceparent。</p>
     *
     * @param traceId 上游传入的 traceId（32 位十六进制）
     * @param spanId  当前服务生成的 spanId（16 位十六进制）
     * @return W3C traceparent header 值
     * @since 1.5.0
     */
    public static String traceparentHeader(String traceId, String spanId) {
        return "00-" + traceId + "-" + spanId + "-01";
    }
}
