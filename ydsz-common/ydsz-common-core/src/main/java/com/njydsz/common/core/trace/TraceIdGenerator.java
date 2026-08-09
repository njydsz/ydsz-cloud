package com.njydsz.common.core.trace;

import java.util.HexFormat;
import java.util.concurrent.ThreadLocalRandom;

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
 * <h3>性能说明</h3>
 * <ul>
 *   <li>默认 {@link #generateTraceId()} 无锁、线程本地，适合绝大多数场景</li>
 *   <li>{@link #generateSortableTraceId()} 按时间排序，使用 ThreadLocal 序列号
 *       避免全局 CAS 争用；同毫秒内不同线程的序号彼此独立但均保序，整体趋势有序</li>
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

    /**
     * 同一毫秒内的序号上限（14 bit，0~16383），允许单线程每毫秒最多 16384 次调用。
     * 超出后进入下一毫秒重新计数。
     */
    private static final int MAX_SEQ_PER_MS = 0x3FFF;

    /**
     * 线程本地的时间戳+序列号状态，避免全局 CAS 争用。
     *
     * <p>每个线程独立维护最后使用的时间戳和同毫秒内的递增序号。
     * 多线程之间不存在锁竞争，整体生成吞吐量更高。</p>
     */
    private static final ThreadLocal<SortableState> SORTABLE_STATE = ThreadLocal.withInitial(SortableState::new);

    private TraceIdGenerator() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 生成 32 位十六进制 TraceId。
     *
     * <p>输出格式与旧版兼容（32 位小写十六进制字符串）。</p>
     *
     * @return 32 位十六进制字符串
     * @since 1.5.0
     * @deprecated 1.9.3 推荐直接使用 {@link #generateSortableTraceId()}，时间有序，便于日志排查和范围检索。
     *             当前实现已委托 {@link #generateSortableTraceId()}，行为等价。
     *             v1.11 起标记 {@code forRemoval = true}，计划 v2.0 移除。请迁移至
     *             {@code TraceIdGenerator.generateSortableTraceId()} 或
     *             {@code TraceIdPropagation.traceHeadersOrCreate()}。
     */
    @Deprecated(since = "1.9.3", forRemoval = true)
    public static String generateTraceId() {
        return generateSortableTraceId();
    }

    /**
     * 生成按时间有序（可排序）的 32 位十六进制 TraceId（UUIDv7 风格）。
     *
     * <p>布局（16 bytes / 32 hex）：</p>
     * <ul>
     *   <li>bytes[0..5]（48 bit）：大端毫秒时间戳</li>
     *   <li>bytes[6..7]（14 bit）：同一毫秒内的单调序号，保证同毫秒内字典序严格递增</li>
     *   <li>bytes[8..15]（66 bit）：随机数，保证唯一性与分布均匀性</li>
     * </ul>
     *
     * <p>相比 {@link #generateTraceId()} 的纯随机实现，本方法生成的 id 可直接用于
     * 日志 / 链路存储的按时间排序与范围检索，便于问题排查。两者输出格式相同（32 位小写 hex），
     * 可共存；默认 {@link #generateTraceId()} 仍保持随机以最大化分布均匀性。</p>
     *
     * <p>本方法使用 ThreadLocal 维护时间戳+序号状态，无全局 CAS 争用，
     * 适合高并发场景下生成有序 traceId。</p>
     *
     * @return 32 位小写十六进制字符串（时间有序）
     * @since 1.9.1
     */
    public static String generateSortableTraceId() {
        byte[] bytes = new byte[TRACE_ID_BYTES];
        SortableState state = SORTABLE_STATE.get();
        long timeMillis = System.currentTimeMillis();
        long seq = state.nextSeq(timeMillis);

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

    /**
     * 可排序 TraceId 的线程本地状态：跟踪上次使用的时间戳和同毫秒内的序号。
     *
     * <p>无需原子操作/锁：每个线程独立维护自己的时间戳和序号，自然线程安全。
     * 同毫秒内单线程严格递增，不同线程间序号独立但整体时间趋势有序。</p>
     */
    private static final class SortableState {
        private long lastMillis;
        private long seq;

        /**
         * 获取下一个序号。
         *
         * <p>时间戳进位时序号归零；时间戳未进步时序号 +1，到上限后自旋等待下一毫秒。</p>
         *
         * @param nowMillis 当前毫秒时间戳
         * @return 当前使用的序号
         */
        synchronized long nextSeq(long nowMillis) {
            if (nowMillis != lastMillis) {
                lastMillis = nowMillis;
                seq = 0;
            } else {
                seq++;
                if (seq > MAX_SEQ_PER_MS) {
                    // 单线程同毫秒调用频次超限，自旋等待下一毫秒
                    while (nowMillis == lastMillis) {
                        nowMillis = System.currentTimeMillis();
                    }
                    lastMillis = nowMillis;
                    seq = 0;
                }
            }
            return seq;
        }
    }
}
