package com.njydsz.common.util.internal.proxy;

/**
 * W3C traceparent 头的解析结果。
 *
 * <p>对应 ydsz-common-core 中 {@code TraceIdGenerator.ParsedTraceparent} Record， 在本模块中作为独立 Record
 * 存在以解除对 core 的编译期依赖。
 *
 * <p>格式：{@code 00-{32hex}-{16hex}-{flags}}
 *
 * @param traceId 32 位十六进制 Trace ID
 * @param spanId 16 位十六进制 Span ID
 * @author ydsz-team
 * @since 26.09.01
 */
public record ParsedTraceparent(String traceId, String spanId) {

  /**
   * 从字符串数组构造 ParsedTraceparent。
   *
   * @param parts 包含 [traceId, spanId] 的数组
   * @return ParsedTraceparent 实例；输入无效返回 null
   */
  public static ParsedTraceparent fromArray(String[] parts) {
    if (parts == null || parts.length < 2) {
      return null;
    }
    return new ParsedTraceparent(parts[0], parts[1]);
  }
}
