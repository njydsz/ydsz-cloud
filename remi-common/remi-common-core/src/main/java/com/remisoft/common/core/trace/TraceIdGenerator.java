package com.remisoft.common.core.trace;

import java.util.HexFormat;
import java.util.concurrent.ThreadLocalRandom;

/**
 * TraceId / SpanId 生成器（纯函数式实现）
 *
 * <p>设计选择：
 * <ul>
 *   <li>不依赖 ThreadLocal 缓冲池 —— 现代 JVM (ZGC/Shenandoah) 的 TLAB 分配在 32 字节以内对象上几乎零成本</li>
 *   <li>不依赖 HexFormat 共享状态 —— 每次调用创建 {@code byte[]} 并转为 hex 字符串</li>
 *   <li>API 通过 {@link SpanContext} 提供结构化访问，不再需要手工拼接 traceId/spanId 字符串</li>
 * </ul>
 *
 * <h3>使用示例：</h3>
 * <pre>{@code
 * // 基础用法（向后兼容）
 * String traceId = TraceIdGenerator.generateTraceId();
 * String spanId = TraceIdGenerator.generateSpanId();
 *
 * // 结构化用法（推荐）
 * SpanContext ctx = SpanContext.newRoot();
 * MDC.put("traceId", ctx.traceId());
 * // 下游调用时传递
 * HttpRequest request = HttpClient.newHttpRequest()
 *     .header("traceparent", ctx.toTraceparent());
 * }</pre>
 *
 * @author remi-team
 * @since 1.0.0
 */
public final class TraceIdGenerator {

    /**
     * traceId 的字节长度（128 bit = 16 bytes）
     */
    public static final int TRACE_ID_BYTES = 16;

    /**
     * spanId 的字节长度（64 bit = 8 bytes）
     */
    public static final int SPAN_ID_BYTES = 8;

    private static final HexFormat HEX = HexFormat.of();

    private TraceIdGenerator() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 生成 32 位十六进制 TraceId（128 bit 随机）
     *
     * @return 32 字符十六进制字符串（小写）
     */
    public static String generateTraceId() {
        byte[] buf = new byte[TRACE_ID_BYTES];
        ThreadLocalRandom.current().nextBytes(buf);
        return HEX.formatHex(buf);
    }

    /**
     * 生成 16 位十六进制 SpanId（64 bit 随机）
     *
     * @return 16 字符十六进制字符串（小写）
     */
    public static String generateSpanId() {
        byte[] buf = new byte[SPAN_ID_BYTES];
        ThreadLocalRandom.current().nextBytes(buf);
        return HEX.formatHex(buf);
    }

    /**
     * 生成 W3C traceparent header 值
     *
     * <p>格式：{@code 00-{traceId}-{spanId}-01}（采样位置 01）
     *
     * @param traceId 32 字符 traceId
     * @param spanId  16 字符 spanId
     * @return W3C traceparent header 值
     */
    public static String traceparentHeader(String traceId, String spanId) {
        return "00-" + traceId + "-" + spanId + "-01";
    }

    /**
     * 生成新的 traceId+spanId 并组合为 W3C traceparent
     *
     * @return 全新的 W3C traceparent 字符串
     */
    public static String newTraceparent() {
        return traceparentHeader(generateTraceId(), generateSpanId());
    }

    /**
     * 生成 W3C traceparent 并携带采样决策
     *
     * @param sampled true = 已采样（trace-flags=01），false = 未采样（trace-flags=00）
     * @return W3C traceparent 字符串
     * @since 1.8.0
     */
    public static String newTraceparent(boolean sampled) {
        String traceId = generateTraceId();
        String spanId = generateSpanId();
        String flags = sampled ? "01" : "00";
        return "00-" + traceId + "-" + spanId + "-" + flags;
    }
}
