package com.njydsz.common.exception.trace;

import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.Nullable;

/**
 * OpenTelemetry 链路追踪信息提取器。
 *
 * <p>在运行时通过反射检测 OpenTelemetry API 是否存在于 classpath：
 *
 * <ul>
 *   <li>若存在：调用 {@code Span.current().getSpanContext()} 提取 traceId / spanId
 *   <li>若不存在（未引入 OTel）：返回 {@link OtelTraceInfo#EMPTY}，无副作用
 * </ul>
 *
 * <p>使用反射而非直接依赖 OpenTelemetry API，避免对未引入 OTel 的项目增加 不必要的依赖（遵循"optional dependency"原则）。
 *
 * <p><b>性能优化（1.0.0）：</b>首次成功后缓存 {@link Method} 对象， 后续调用直接使用缓存的 Method.invoke，消除重复的 getMethod 查找开销。
 * 使用 {@link AtomicBoolean} 保证缓存初始化的线程安全。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public class OtelTraceInfoExtractor {

  private OtelTraceInfoExtractor() {}

  /** OTel Span 类全限定名（反射用） */
  private static final String OTEL_SPAN_CLASS = "io.opentelemetry.api.trace.Span";

  /** OTel SpanContext 类全限定名 */
  private static final String OTEL_SPAN_CONTEXT_CLASS = "io.opentelemetry.api.trace.SpanContext";

  /**
   * OTel 是否可用的标记。
   *
   * <p>首次调用后缓存，避免重复 Class.forName。
   */
  @Nullable private static volatile Boolean otelAvailable;

  /** Method 缓存初始化标志（CAS 保证只初始化一次） */
  private static final AtomicBoolean METHOD_CACHE_INITIALIZED = new AtomicBoolean(false);

  /** 缓存的 Span.current() 静态方法 */
  @Nullable private static volatile Method currentMethod;

  /** 缓存的 getSpanContext() 方法 */
  @Nullable private static volatile Method getSpanContextMethod;

  /** 缓存的 getTraceId() 方法 */
  @Nullable private static volatile Method getTraceIdMethod;

  /** 缓存的 getSpanId() 方法 */
  @Nullable private static volatile Method getSpanIdMethod;

  /** 缓存的 isValid() 方法 */
  @Nullable private static volatile Method isValidMethod;

  /** 缓存的 isSampled() 方法 */
  @Nullable private static volatile Method isSampledMethod;

  /**
   * 获取当前线程的 OpenTelemetry 链路追踪信息。
   *
   * <p>无论 OTel 是否接入，本方法都不会抛异常——OTel 未接入时返回 {@link OtelTraceInfo#EMPTY}，保证主流程零侵入。
   *
   * @return OtelTraceInfo 实例（永不为 null）
   */
  public static OtelTraceInfo currentTraceInfo() {
    if (!isOtelAvailable()) {
      return OtelTraceInfo.EMPTY;
    }
    try {
      // 懒初始化 Method 缓存（首次调用时执行）
      ensureMethodCacheInitialized();

      // Span span = Span.current();
      Object span = currentMethod != null ? currentMethod.invoke(null) : null;
      if (span == null) {
        return OtelTraceInfo.EMPTY;
      }
      // SpanContext ctx = span.getSpanContext();
      Object spanContext = getSpanContextMethod != null ? getSpanContextMethod.invoke(span) : null;
      if (spanContext == null) {
        return OtelTraceInfo.EMPTY;
      }
      // String traceId = ctx.getTraceId();
      String traceId =
          getTraceIdMethod != null ? (String) getTraceIdMethod.invoke(spanContext) : null;
      // String spanId = ctx.getSpanId();
      String spanId = getSpanIdMethod != null ? (String) getSpanIdMethod.invoke(spanContext) : null;
      // boolean valid = ctx.isValid();
      boolean valid = isValidMethod != null && (Boolean) isValidMethod.invoke(spanContext);

      if (!valid) {
        return OtelTraceInfo.EMPTY;
      }

      boolean sampled = isSampledMethod != null && (Boolean) isSampledMethod.invoke(spanContext);

      return new OtelTraceInfo(traceId, spanId, sampled);
    } catch (Exception e) {
      // OTel 运行时异常（如 ClassLoader 变化等），降级为不可用
      log.debug("[OtelTraceInfoExtractor] 提取 OTel TraceInfo 失败: {}", e.getMessage());
      // 清除缓存，下次调用重新初始化
      METHOD_CACHE_INITIALIZED.set(false);
      clearMethodCache();
      return OtelTraceInfo.EMPTY;
    }
  }

  /**
   * 检测 OpenTelemetry API 是否可用。
   *
   * @return true-classpath 中存在 OTel API
   */
  public static boolean isOtelAvailable() {
    Boolean cached = otelAvailable;
    if (cached != null) {
      return cached;
    }
    try {
      Class.forName(OTEL_SPAN_CLASS);
      otelAvailable = Boolean.TRUE;
      return true;
    } catch (ClassNotFoundException e) {
      otelAvailable = Boolean.FALSE;
      return false;
    }
  }

  /**
   * 确保 Method 缓存已初始化（CAS 保证幂等）。
   *
   * <p>仅在首次调用或缓存失效后执行，避免不必要的反射查找。
   */
  private static void ensureMethodCacheInitialized() {
    if (METHOD_CACHE_INITIALIZED.get()) {
      return;
    }
    if (METHOD_CACHE_INITIALIZED.compareAndSet(false, true)) {
      try {
        Class<?> spanClass = Class.forName(OTEL_SPAN_CLASS);
        Class<?> spanContextClass = Class.forName(OTEL_SPAN_CONTEXT_CLASS);

        currentMethod = spanClass.getMethod("current");
        // SpanContext 的方法定义在 SpanContext 类上
        getSpanContextMethod = spanClass.getMethod("getSpanContext");
        getTraceIdMethod = spanContextClass.getMethod("getTraceId");
        getSpanIdMethod = spanContextClass.getMethod("getSpanId");
        isValidMethod = spanContextClass.getMethod("isValid");
        isSampledMethod = spanContextClass.getMethod("isSampled");
      } catch (Exception e) {
        // 初始化失败，清除标志让下次重试
        METHOD_CACHE_INITIALIZED.set(false);
        log.debug("[OtelTraceInfoExtractor] Method 缓存初始化失败: {}", e.getMessage());
      }
    }
  }

  /** 清除 Method 缓存（反射异常时调用，让下次重新初始化） */
  private static void clearMethodCache() {
    currentMethod = null;
    getSpanContextMethod = null;
    getTraceIdMethod = null;
    getSpanIdMethod = null;
    isValidMethod = null;
    isSampledMethod = null;
  }
}
