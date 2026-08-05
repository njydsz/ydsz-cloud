package com.remisoft.common.core.trace;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Span 上下文（封装 traceId + spanId + traceFlags + traceState 四元组）
 *
 * <p>符合 W3C Trace Context 标准的 Span 数据结构，提供与多种分布式追踪协议的互转方法。
 *
 * <h3>支持的协议格式：</h3>
 * <ul>
 *   <li><b>W3C traceparent</b>：{@code 00-{traceId32}-{spanId16}-{flags2}} —— 标准跨 vendor 协议</li>
 *   <li><b>W3C tracestate</b>：{@code vendor1=value1,vendor2=value2} —— 供应商特定上下文</li>
 *   <li><b>B3 single header</b>：{@code {traceId32}-{spanId16}-{sampling}} —— Zipkin 协议</li>
 *   <li><b>SkyWalking 3.x</b>：{@code {traceId}.{spanId}.{parentSegmentId}.{sample}—— 已废弃但仍需兼容</li>
 * </ul>
 *
 * <p><b>线程安全：</b>record 天生 immutable，多线程安全。
 *
 * @param traceId    追踪 ID（32 字符十六进制）
 * @param spanId     Span ID（16 字符十六进制）
 * @param traceFlags 跟踪标志（00 = 未采样，01 = 已采样）
 * @param traceState 跟踪状态（W3C tracestate 键值对）
 * @author remi-team
 * @since 1.8.0
 */
public record SpanContext(
    String traceId,
    String spanId,
    String traceFlags,
    List<TraceStateEntry> traceState
) implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * W3C 默认版本
     */
    public static final String W3C_VERSION = "00";

    /**
     * 已采样标志位
     */
    public static final String FLAGS_SAMPLED = "01";

    /**
     * 未采样标志位
     */
    public static final String FLAGS_NOT_SAMPLED = "00";

    /**
     * compact constructor —— 参数归一化
     */
    public SpanContext {
        Objects.requireNonNull(traceId, "traceId must not be null");
        Objects.requireNonNull(spanId, "spanId must not be null");
        traceFlags = traceFlags != null ? traceFlags : FLAGS_SAMPLED;
        traceState = traceState != null ? List.copyOf(traceState) : List.of();
    }

    /**
     * 简化构造（无 traceState、默认已采样）
     */
    public SpanContext(String traceId, String spanId) {
        this(traceId, spanId, FLAGS_SAMPLED, List.of());
    }

    // -------------------------------------------------------------------------
    // 静态工厂
    // -------------------------------------------------------------------------

    /**
     * 创建跟随当前进程的根 Span（全新 traceId + spanId）
     *
     * @return 新的 SpanContext
     */
    public static SpanContext newRoot() {
        return new SpanContext(
            TraceIdGenerator.generateTraceId(),
            TraceIdGenerator.generateSpanId(),
            FLAGS_SAMPLED,
            List.of()
        );
    }

    /**
     * 创建根 Span，并指定是否采样
     *
     * @param sampled 是否采样（true=01，false=00）
     * @return 新的 SpanContext
     */
    public static SpanContext newRoot(boolean sampled) {
        return new SpanContext(
            TraceIdGenerator.generateTraceId(),
            TraceIdGenerator.generateSpanId(),
            sampled ? FLAGS_SAMPLED : FLAGS_NOT_SAMPLED,
            List.of()
        );
    }

    /**
     * 解析 W3C traceparent header
     *
     * @param traceparent 如 "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01"
     * @return 解析后的 SpanContext
     * @throws IllegalArgumentException 格式非法时抛出
     */
    public static SpanContext fromTraceparent(String traceparent) {
        if (traceparent == null || traceparent.isEmpty()) {
            return newRoot();
        }
        String[] parts = traceparent.split("-");
        if (parts.length != 4) {
            throw new IllegalArgumentException("Invalid W3C traceparent format: " + traceparent);
        }
        String version = parts[0];
        if (!W3C_VERSION.equals(version)) {
            throw new IllegalArgumentException("Unsupported traceparent version: " + version);
        }
        return new SpanContext(parts[1], parts[2], parts[3], List.of());
    }

    /**
     * 解析 B3 single header (Zipkin)
     *
     * @param b3Header 如 "0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-1"
     * @return 解析后的 SpanContext
     */
    public static SpanContext fromB3Single(String b3Header) {
        if (b3Header == null || b3Header.isEmpty()) {
            return newRoot();
        }
        String[] parts = b3Header.split("-");
        if (parts.length < 2) {
            throw new IllegalArgumentException("Invalid B3 single header format: " + b3Header);
        }
        boolean sampled = parts.length >= 3 && "1".equals(parts[2]);
        return new SpanContext(parts[0], parts[1], sampled ? FLAGS_SAMPLED : FLAGS_NOT_SAMPLED, List.of());
    }

    // -------------------------------------------------------------------------
    // W3C 协议互转
    // -------------------------------------------------------------------------

    /**
     * 序列化为 W3C traceparent header 值
     *
     * @return 如 "00-0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-01"
     */
    public String toTraceparent() {
        return W3C_VERSION + "-" + traceId + "-" + spanId + "-" + traceFlags;
    }

    /**
     * 序列化为 W3C tracestate header 值
     *
     * @return 如 "remi=s:1,tdm=t.trace_id:0af7651916cd43dd8448eb211c80319c"
     */
    public String toTracestate() {
        if (traceState.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < traceState.size(); i++) {
            if (i > 0) sb.append(',');
            TraceStateEntry entry = traceState.get(i);
            sb.append(entry.key()).append('=').append(entry.value());
        }
        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // B3 协议互转
    // -------------------------------------------------------------------------

    /**
     * 序列化为 B3 single header (Zipkin)
     *
     * @return 如 "0af7651916cd43dd8448eb211c80319c-b7ad6b7169203331-1"
     */
    public String toB3Single() {
        return traceId + "-" + spanId + "-" + (isSampled() ? "1" : "0");
    }

    /**
     * 转为 B3 多header键值对 (X-B3-TraceId 等)
     *
     * @return B3 header 键值对列表
     */
    public List<B3Header> toB3Headers() {
        List<B3Header> headers = new ArrayList<>(4);
        headers.add(new B3Header("X-B3-TraceId", traceId));
        headers.add(new B3Header("X-B3-SpanId", spanId));
        headers.add(new B3Header("X-B3-Sampled", isSampled() ? "1" : "0"));
        return headers;
    }

    // -------------------------------------------------------------------------
    // SkyWalking 协议互转
    // -------------------------------------------------------------------------

    /**
     * 序列化为 SkyWalking 3.x 数据协议
     *
     * @return 如 "0af7651916cd43dd8448eb211c80319c.0.0.1"
     */
    public String toSkyWalking() {
        return traceId + ".0.0." + (isSampled() ? "1" : "0");
    }

    // -------------------------------------------------------------------------
    // 便捷状态查询
    // -------------------------------------------------------------------------

    /**
     * 当前 Span 是否已采样
     */
    public boolean isSampled() {
        return FLAGS_SAMPLED.equals(traceFlags);
    }

    /**
     * 创建子 Span（同一 traceId，新 spanId）
     *
     * @return 新的 SpanContext
     */
    public SpanContext newChild() {
        return new SpanContext(traceId, TraceIdGenerator.generateSpanId(), traceFlags, traceState);
    }

    /**
     * 返回已替换 traceState 的副本
     */
    public SpanContext withTraceState(List<TraceStateEntry> newState) {
        return new SpanContext(traceId, spanId, traceFlags, newState);
    }

    /**
     * 添加一个 tracestate 条目
     */
    public SpanContext withTraceStateEntry(String key, String value) {
        List<TraceStateEntry> newState = new ArrayList<>(traceState);
        newState.add(new TraceStateEntry(key, value));
        return newSpanContextWithState(newState);
    }

    private SpanContext newSpanContextWithState(List<TraceStateEntry> newState) {
        return new SpanContext(traceId, spanId, traceFlags, newState);
    }

    // -------------------------------------------------------------------------
    // 内部类型
    // -------------------------------------------------------------------------

    /**
     * trace-state 条目
     *
     * @param key   键名
     * @param value 值
     */
    public record TraceStateEntry(String key, String value) implements Serializable {
        private static final long serialVersionUID = 1L;
    }

    /**
     * B3 header 键值对
     *
     * @param name  header 名称
     * @param value header 值
     */
    public record B3Header(String name, String value) implements Serializable {
        private static final long serialVersionUID = 1L;
    }
}
