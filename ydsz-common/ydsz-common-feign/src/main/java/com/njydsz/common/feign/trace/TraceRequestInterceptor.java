package com.njydsz.common.feign.trace;

import feign.RequestInterceptor;
import feign.RequestTemplate;

import com.njydsz.common.core.constant.HeaderConstants;
import com.njydsz.common.core.trace.TraceIdPropagation;
import com.njydsz.common.util.id.TracerUtils;
import com.njydsz.common.util.string.StringUtils;

/**
 * 链路追踪请求拦截器。
 *
 * <p>自动为 Feign 请求注入链路追踪相关请求头，实现微服务调用链追踪。
 *
 * <p><b>注入的请求头：</b>
 *
 * <ul>
 *   <li>{@code X-Trace-Id} - 追踪唯一标识（兼容旧系统）
 *   <li>{@code X-Span-Id} - Span 唯一标识
 *   <li>{@code X-Parent-Span-Id} - 父 Span 标识
 *   <li>{@code traceparent} - W3C TraceContext 标准头（格式: 00-{traceId}-{spanId}-{flags}）
 * </ul>
 *
 * <p><b>追踪策略优先级：</b>
 *
 * <ol>
 *   <li>当 SkyWalking agent 在 classpath 中时，使用 SkyWalking 的 traceId/spanId
 *   <li>当 W3C traceparent 存在于请求头中时，延续该 trace 链路
 *   <li>降级为 TracerUtils 生成自定义 traceId/spanId
 * </ol>
 *
 * <p><b>与 {@link TraceIdPropagation} 的协作：</b>
 *
 * <ul>
 *   <li>标准场景由 {@link TraceIdPropagation#currentTraceIdOrCreate()} 统一解析/创建 traceId
 *   <li>本拦截器补充分支 spanId/parentSpanId 透传和 W3C traceparent 封装
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see FeignTraceHandler
 * @see TraceIdPropagation
 * @see com.njydsz.common.core.trace.TraceIdGenerator
 */
public class TraceRequestInterceptor implements RequestInterceptor {

  /** 追踪 ID 请求头名称（兼容旧系统） */
  private static final String HEADER_TRACE_ID = HeaderConstants.TRACE_ID_HEADER;

  /** Span ID 请求头名称 */
  private static final String HEADER_SPAN_ID = "X-Span-Id";

  /** 父 Span ID 请求头名称 */
  private static final String HEADER_PARENT_SPAN_ID = "X-Parent-Span-Id";

  /** W3C TraceContext 标准头 */
  private static final String HEADER_TRACEPARENT = "traceparent";

  /** W3C traceparent 版本前缀 */
  private static final String TRACEPARENT_VERSION = "00";

  /** W3C traceparent flags（sampled） */
  private static final String TRACEPARENT_FLAGS_SAMPLED = "01";

  /** 链路追踪处理器 */
  private final FeignTraceHandler traceHandler;

  /** 是否启用 W3C traceparent 传播 */
  private final boolean w3cEnabled;

  /** 使用默认追踪处理器构造拦截器。 */
  public TraceRequestInterceptor() {
    this(new DefaultTraceHandler(), true);
  }

  /**
   * 使用指定追踪处理器构造拦截器，默认启用 W3C 传播。
   *
   * @param traceHandler 链路追踪处理器
   */
  public TraceRequestInterceptor(FeignTraceHandler traceHandler) {
    this(traceHandler, true);
  }

  /**
   * 使用指定追踪处理器和 W3C 开关构造拦截器。
   *
   * @param traceHandler 链路追踪处理器
   * @param w3cEnabled 是否启用 W3C traceparent 传播
   */
  public TraceRequestInterceptor(FeignTraceHandler traceHandler, boolean w3cEnabled) {
    this.traceHandler = traceHandler != null ? traceHandler : new DefaultTraceHandler();
    this.w3cEnabled = w3cEnabled;
  }

  /**
   * 为 Feign 请求注入链路追踪相关请求头。
   *
   * <p>自动解析或生成 traceId 和 spanId，并注入到请求头中， 同时通过 {@link FeignTraceHandler} 记录调用开始事件。
   *
   * @param requestTemplate Feign 请求模板
   */
  @Override
  public void apply(RequestTemplate requestTemplate) {
    String traceId = resolveTraceId(requestTemplate);
    String spanId = TracerUtils.generateSpanId();
    String parentSpanId = resolveParentSpanId(requestTemplate);

    if (StringUtils.isEmpty(traceId)) {
      traceId = TracerUtils.generateTraceId();
    }

    requestTemplate.header(HEADER_TRACE_ID, traceId);
    requestTemplate.header(HEADER_SPAN_ID, spanId);

    if (StringUtils.isNotEmpty(parentSpanId)) {
      requestTemplate.header(HEADER_PARENT_SPAN_ID, parentSpanId);
    }

    if (w3cEnabled) {
      String traceparent = buildTraceparent(traceId, spanId);
      if (traceparent != null) {
        requestTemplate.header(HEADER_TRACEPARENT, traceparent);
      }
    }

    if (traceHandler != null && traceHandler.isEnabled()) {
      FeignTraceHandler.TraceContext context = new FeignTraceHandler.TraceContext();
      context.setTraceId(traceId);
      context.setSpanId(spanId);
      context.setParentSpanId(parentSpanId);
      context.setUrl(requestTemplate.url());
      context.setHttpMethod(requestTemplate.method());
      traceHandler.onRequestStart(context);
    }
  }

  /**
   * 构建 W3C traceparent 头。
   *
   * <p>格式：{@code 00-{traceId(32hex)}-{spanId(16hex)}-{flags(2hex)}}
   *
   * @param traceId 追踪 ID
   * @param spanId Span ID
   * @return W3C traceparent 字符串，无法构建时返回 null
   */
  private String buildTraceparent(String traceId, String spanId) {
    if (StringUtils.isEmpty(traceId) || StringUtils.isEmpty(spanId)) {
      return null;
    }
    String normalizedTraceId = normalizeToHex(traceId, 32);
    String normalizedSpanId = normalizeToHex(spanId, 16);
    return TRACEPARENT_VERSION
        + "-"
        + normalizedTraceId
        + "-"
        + normalizedSpanId
        + "-"
        + TRACEPARENT_FLAGS_SAMPLED;
  }

  /**
   * 将字符串规范化为指定长度的十六进制字符串。
   *
   * @param value 原始值
   * @param length 目标长度
   * @return 规范化后的十六进制字符串
   */
  private String normalizeToHex(String value, int length) {
    String hex = value.replaceAll("[^0-9a-fA-F]", "");
    if (hex.length() >= length) {
      return hex.substring(0, length).toLowerCase();
    }
    return String.format("%" + length + "s", hex).replace(' ', '0').toLowerCase();
  }

  /**
   * 解析追踪 ID，优先从请求头获取，其次从追踪处理器获取当前上下文的 traceId。
   *
   * @param requestTemplate Feign 请求模板
   * @return 追踪 ID，无法获取时返回 null
   */
  private String resolveTraceId(RequestTemplate requestTemplate) {
    String traceId =
        requestTemplate.headers().get(HEADER_TRACE_ID) != null
            ? requestTemplate.headers().get(HEADER_TRACE_ID).iterator().next()
            : null;
    if (StringUtils.isNotEmpty(traceId)) {
      return traceId;
    }
    if (traceHandler != null) {
      return traceHandler.getCurrentTraceId();
    }
    return null;
  }

  /**
   * 解析父 Span ID，优先从请求头获取当前 Span ID 作为父 Span ID，其次从追踪处理器获取。
   *
   * @param requestTemplate Feign 请求模板
   * @return 父 Span ID，无法获取时返回 null
   */
  private String resolveParentSpanId(RequestTemplate requestTemplate) {
    String parentSpanId =
        requestTemplate.headers().get(HEADER_SPAN_ID) != null
            ? requestTemplate.headers().get(HEADER_SPAN_ID).iterator().next()
            : null;
    if (StringUtils.isNotEmpty(parentSpanId)) {
      return parentSpanId;
    }
    if (traceHandler != null) {
      return traceHandler.getCurrentSpanId();
    }
    return null;
  }

  /**
   * 默认追踪处理器。
   *
   * <p>提供基础的链路追踪能力，生成 TraceId 和 SpanId。
   */
  private static class DefaultTraceHandler implements FeignTraceHandler {

    @Override
    public String getName() {
      return "default";
    }

    @Override
    public String getCurrentTraceId() {
      // 统一使用 TraceIdPropagation 作为 traceId 源，与框架标准入口对齐
      return TraceIdPropagation.currentTraceId();
    }

    @Override
    public String getCurrentSpanId() {
      return TracerUtils.getSpanId();
    }
  }
}
