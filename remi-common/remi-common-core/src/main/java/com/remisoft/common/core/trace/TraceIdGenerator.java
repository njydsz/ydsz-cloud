package com.remisoft.common.core.trace;

import java.util.HexFormat;
import java.util.concurrent.ThreadLocalRandom;

/**
 * TraceId 生成器（基于 ThreadLocalRandom + ThreadLocal 字节缓冲）。
 */
public final class TraceIdGenerator {

    private static final int TRACE_ID_BYTES = 16;
    private static final int SPAN_ID_BYTES = 8;

    private static final ThreadLocal<byte[]> TRACE_BUF = ThreadLocal.withInitial(() -> new byte[TRACE_ID_BYTES]);
    private static final ThreadLocal<byte[]> SPAN_BUF = ThreadLocal.withInitial(() -> new byte[SPAN_ID_BYTES]);
    private static final HexFormat HEX = HexFormat.of();

    private TraceIdGenerator() {
    }

    /**
     * 生成 32 位十六进制 TraceId。
     */
    public static String generateTraceId() {
        byte[] buf = TRACE_BUF.get();
        ThreadLocalRandom.current().nextBytes(buf);
        return HEX.formatHex(buf);
    }

    /**
     * 生成 16 位十六进制 SpanId。
     */
    public static String generateSpanId() {
        byte[] buf = SPAN_BUF.get();
        ThreadLocalRandom.current().nextBytes(buf);
        return HEX.formatHex(buf);
    }

    /**
     * 生成 W3C traceparent header 值。
     */
    public static String traceparentHeader(String traceId, String spanId) {
        return "00-" + traceId + "-" + spanId + "-01";
    }

    /**
     * 生成新的 traceId+spanId 并组合为 W3C traceparent。
     */
    public static String newTraceparent() {
        return traceparentHeader(generateTraceId(), generateSpanId());
    }
}
