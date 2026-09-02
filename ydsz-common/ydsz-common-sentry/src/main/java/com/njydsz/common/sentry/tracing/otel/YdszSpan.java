package com.njydsz.common.sentry.tracing.otel;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import lombok.extern.slf4j.Slf4j;

/**
 * YDSZ Span 构建器（业务友好的 OTel API 封装）
 *
 * <p>对 OTel API 的链式 Builder 进行业务向封装，提供：
 *
 * <ul>
 *   <li>统一注入租户 / 用户 / 业务单号 / 灰度标签等 YDSZ 自定义属性
 *   <li>try-with-resources 风格的 Scope 自动关闭
 *   <li>异常自动记录（异常类型、消息、堆栈摘要）
 *   <li>常用预置 Span（DB / HTTP / MQ）
 * </ul>
 *
 * <p>使用示例：
 *
 * <pre>{@code
 * // 业务 Span
 * YdszSpan span = YdszSpan.builder(tracer, "order.create")
 *         .kind(SpanKind.INTERNAL)
 *         .tenantId("acme")
 *         .userId("u-1001")
 *         .module("order")
 *         .action("create")
 *         .tag("orderType", "B2B")
 *         .start();
 *
 * try (Scope ignored = span.scope()) {
 *     // 业务逻辑
 *     orderService.create(req);
 * } catch (Exception e) {
 *     span.recordException(e);
 *     span.error("ORDER_CREATE_FAILED", e.getMessage());
 *     throw e;
 * } finally {
 *     span.end();
 * }
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
public final class YdszSpan {

  private final Span span;
  private final long startNanos;

  private YdszSpan(Span span) {
    this.span = span;
    this.startNanos = System.nanoTime();
  }

  /**
   * 创建 Builder。
   *
   * @param tracer OTel Tracer（可通过 {@code Sentry.getTracer()} 获取）
   * @param spanName Span 名称
   * @return Builder 实例
   */
  public static Builder builder(Tracer tracer, String spanName) {
    return new Builder(tracer, spanName);
  }

  /**
   * 创建带命名空间的 Builder。
   *
   * @param tracer OTel Tracer
   * @param namespace 命名空间
   * @param operation 操作名
   * @return Builder 实例
   */
  public static Builder builder(Tracer tracer, String namespace, String operation) {
    return new Builder(tracer, OtelSemConv.spanName(namespace, operation));
  }

  /**
   * 获取底层 OTel Span。
   *
   * @return 底层 OTel Span
   */
  public Span span() {
    return span;
  }

  /** 设置属性 */
  /**
   * set attribute。
   * @param key 参数
   * @param value 参数
   * @return 结果
   */
  public YdszSpan setAttribute(String key, String value) {
    if (value != null) {
      span.setAttribute(key, value);
    }
    return this;
  }

  /**
   * 设置长整型属性。
   *
   * @param key 属性名
   * @param value 属性值
   * @return 当前 Span 包装（链式调用）
   */
  public YdszSpan setAttribute(String key, long value) {
    span.setAttribute(key, value);
    return this;
  }

  /**
   * 设置布尔型属性。
   *
   * @param key 属性名
   * @param value 属性值
   * @return 当前 Span 包装（链式调用）
   */
  public YdszSpan setAttribute(String key, boolean value) {
    span.setAttribute(key, value);
    return this;
  }

  /**
   * 设置长整型属性（使用预声明的 AttributeKey，避免重复分配）。
   *
   * @param key AttributeKey（Long）
   * @param value 属性值
   * @return 当前 Span 包装（链式调用）
   */
  public YdszSpan setAttribute(AttributeKey<Long> key, long value) {
    span.setAttribute(key, value);
    return this;
  }

  /**
   * 设置字符串属性（使用预声明的 AttributeKey）。
   *
   * <p>{@code value} 为 {@code null} 时不写入，避免 OTel 对 null 属性的告警。
   *
   * @param key AttributeKey（String）
   * @param value 属性值；{@code null} 时跳过
   * @return 当前 Span 包装（链式调用）
   */
  public YdszSpan setAttribute(AttributeKey<String> key, String value) {
    if (value != null) {
      span.setAttribute(key, value);
    }
    return this;
  }

  /** 批量设置属性 */
  /**
   * set attributes。
   * @param attrs 参数
   * @return 结果
   */
  public YdszSpan setAttributes(Map<String, Object> attrs) {
    if (attrs != null && !attrs.isEmpty()) {
      Attributes otelAttrs = OtelSemConv.toAttributes(attrs);
      span.setAllAttributes(otelAttrs);
    }
    return this;
  }

  /** 记录异常 */
  /**
   * record exception。
   * @param t 参数
   * @return 结果
   */
  public YdszSpan recordException(Throwable t) {
    if (t != null) {
      span.recordException(t);
      return this;
    }
    return this;
  }

  /** 标记为错误并设置状态 */
  /**
   * error。
   * @param errorCode 参数
   * @param message 参数
   * @return 结果
   */
  public YdszSpan error(String errorCode, String message) {
    if (errorCode != null) {
      span.setAttribute(OtelSemConv.REMI_ERROR_CODE, errorCode);
    }
    if (message != null) {
      span.setStatus(StatusCode.ERROR, message);
    } else {
      span.setStatus(StatusCode.ERROR);
    }
    return this;
  }

  /** 标记为 OK */
  /**
   * ok。
   * @return 结果
   */
  public YdszSpan ok() {
    span.setStatus(StatusCode.OK);
    return this;
  }

  /** 激活 Span 上下文（必须配合 try-with-resources 关闭） */
  /**
   * scope。
   * @return 结果
   */
  public Scope scope() {
    return span.makeCurrent();
  }

  /** 获取当前激活 Span */
  /**
   * current。
   * @return 结果
   */
  public static Span current() {
    return Span.current();
  }

  /** 结束 Span（必须调用，否则内存泄漏） */
  /**
   * end。
   */
  public void end() {
    try {
      span.end();
    } catch (Exception e) {
      log.debug("[YdszSpan] 结束 Span 失败: {}", e.getMessage());
    }
  }

  /** 获取自构造以来的纳秒数 */
  /**
   * elapsed nanos。
   * @return 结果
   */
  public long elapsedNanos() {
    return System.nanoTime() - startNanos;
  }

  /** 获取自构造以来的毫秒数 */
  /**
   * elapsed millis。
   * @return 结果
   */
  public long elapsedMillis() {
    return TimeUnit.NANOSECONDS.toMillis(elapsedNanos());
  }

  // ============================================================================
  // Builder
  // ============================================================================

  /** Span 构建器 */
  public static class Builder {
    private final SpanBuilder builder;

    /**
     * 构造 Builder。
     *
     * @param tracer OTel Tracer
     * @param spanName Span 名称
     */
    public Builder(Tracer tracer, String spanName) {
      this.builder = tracer.spanBuilder(spanName);
    }

    /**
     * 指定 Span 类型，决定 APM 后端的拓扑归类（入口 / 出口 / 内部 / MQ 生产消费）。
     *
     * <p>不设置时 OTel 默认按 {@link SpanKind#INTERNAL} 处理，服务拓扑图上不会体现为跨进程调用。
     *
     * @param kind Span 类型，不可为 {@code null}
     * @return 当前 Builder，支持链式调用
     */
    public Builder kind(SpanKind kind) {
      builder.setSpanKind(kind);
      return this;
    }

    /**
     * 显式指定父 Span，用于跨线程 / 异步任务中手动续接链路。
     *
     * <p>不调用时沿用当前线程 {@link Context} 中的 Span；异步场景下线程上下文已丢失， 必须在提交任务前捕获父 Span 并在此传入，否则会断链生成孤立 Trace。
     *
     * @param parent 父 Span；传 {@code null} 表示不改变默认父级推断行为
     * @return 当前 Builder，支持链式调用
     */
    public Builder parent(Span parent) {
      if (parent != null) {
        builder.setParent(Context.current().with(parent));
      }
      return this;
    }

    /**
     * 标记所属租户，是多租户环境下按租户维度切分链路与告警的关键属性。
     *
     * @param tenantId 租户标识；{@code null} 或空串将被忽略，不写入空属性
     * @return 当前 Builder，支持链式调用
     */
    public Builder tenantId(String tenantId) {
      return setAttr(OtelSemConv.REMI_TENANT_ID, tenantId);
    }

    /**
     * 标记操作发起用户，用于按人排查问题与审计关联。
     *
     * <p>注意该属性会随 Span 上报至 APM 后端，不要传入手机号、身份证等敏感明文。
     *
     * @param userId 用户标识；{@code null} 或空串将被忽略
     * @return 当前 Builder，支持链式调用
     */
    public Builder userId(String userId) {
      return setAttr(OtelSemConv.REMI_USER_ID, userId);
    }

    /**
     * 标记业务单号（订单号、工单号等），支持按单号反查完整调用链。
     *
     * @param businessNo 业务单号；{@code null} 或空串将被忽略
     * @return 当前 Builder，支持链式调用
     */
    public Builder businessNo(String businessNo) {
      return setAttr(OtelSemConv.REMI_BUSINESS_NO, businessNo);
    }

    /**
     * 标记所属业务模块，用于按模块聚合耗时与错误率。
     *
     * @param module 模块名，建议与服务内包名/领域名保持一致；{@code null} 或空串将被忽略
     * @return 当前 Builder，支持链式调用
     */
    public Builder module(String module) {
      return setAttr(OtelSemConv.REMI_MODULE, module);
    }

    /**
     * 标记业务动作（如 {@code create}、{@code approve}），与 module 组合成二级分析维度。
     *
     * @param action 动作名；{@code null} 或空串将被忽略
     * @return 当前 Builder，支持链式调用
     */
    public Builder action(String action) {
      return setAttr(OtelSemConv.REMI_ACTION, action);
    }

    /**
     * 标记调用端类型（如 web / app / openapi），用于区分同一接口的不同来源质量。
     *
     * @param clientType 客户端类型；{@code null} 或空串将被忽略
     * @return 当前 Builder，支持链式调用
     */
    public Builder clientType(String clientType) {
      return setAttr(OtelSemConv.REMI_CLIENT_TYPE, clientType);
    }

    /**
     * 标记灰度标签，尾部采样规则会对命中灰度标签的链路做 100% 保留。
     *
     * @param grayTag 灰度标签；{@code null} 或空串将被忽略
     * @return 当前 Builder，支持链式调用
     * @see SpanEvaluationProcessor
     */
    public Builder grayTag(String grayTag) {
      return setAttr(OtelSemConv.REMI_GRAY_TAG, grayTag);
    }

    /**
     * 标记压测流量，便于在监控大盘与告警中剔除压测数据、避免污染真实业务指标。
     *
     * @param pressureTag 压测标识；{@code null} 或空串将被忽略
     * @return 当前 Builder，支持链式调用
     */
    public Builder pressureTag(String pressureTag) {
      return setAttr(OtelSemConv.REMI_PRESSURE_TAG, pressureTag);
    }

    /**
     * 追加自定义字符串属性。
     *
     * <p>属性基数（cardinality）过高会显著增加后端存储与查询开销， 不要把 UUID、时间戳等无限取值写入 key 或 value。
     *
     * @param key 属性名；{@code null} 时整体忽略
     * @param value 属性值；{@code null} 或空串时整体忽略
     * @return 当前 Builder，支持链式调用
     */
    public Builder tag(String key, String value) {
      if (key != null && value != null && !value.isEmpty()) {
        builder.setAttribute(key, value);
      }
      return this;
    }

    /**
     * 追加自定义数值属性，适用于条数、字节数、耗时等可聚合指标。
     *
     * @param key 属性名，不可为 {@code null}
     * @param value 属性值
     * @return 当前 Builder，支持链式调用
     */
    public Builder tag(String key, long value) {
      builder.setAttribute(key, value);
      return this;
    }

    /**
     * 批量追加属性，值类型由 {@link OtelSemConv#toAttributes} 负责映射为 OTel 支持的类型。
     *
     * @param attrs 属性集合；{@code null} 或空 Map 时不做任何操作
     * @return 当前 Builder，支持链式调用
     */
    public Builder attributes(Map<String, Object> attrs) {
      if (attrs != null && !attrs.isEmpty()) {
        builder.setAllAttributes(OtelSemConv.toAttributes(attrs));
      }
      return this;
    }

    private Builder setAttr(AttributeKey<String> key, String value) {
      if (value != null && !value.isEmpty()) {
        builder.setAttribute(key, value);
      }
      return this;
    }

    /** 启动 Span */
    /**
     * start。
     * @return 结果
     */
    public YdszSpan start() {
      return new YdszSpan(builder.startSpan());
    }
  }

  // ============================================================================
  // 静态便捷方法
  // ============================================================================

  /**
   * 执行一个有 Span 包裹的操作
   *
   * @param tracer OTel Tracer
   * @param name Span 名称
   * @param action 要执行的操作
   * @param <T> 返回类型
   * @return 操作结果
   */
  public static <T> T run(Tracer tracer, String name, Supplier<T> action) {
    YdszSpan span = builder(tracer, name).start();
    try (Scope ignored = span.scope()) {
      T result = action.get();
      span.ok();
      return result;
    } catch (RuntimeException e) {
      span.recordException(e);
      span.error("RUNTIME_ERROR", e.getMessage());
      throw e;
    } finally {
      span.end();
    }
  }

  /** 无返回值的便捷方法 */
  /**
   * run void。
   * @param tracer 参数
   * @param name 参数
   * @param action 参数
   */
  public static void runVoid(Tracer tracer, String name, Runnable action) {
    run(
        tracer,
        name,
        () -> {
          action.run();
          return null;
        });
  }

  /** 批量导出快捷方法：把 Map 转为属性集合 */
  /**
   * attrs。
   * @return 结果
   */
  public static Map<String, Object> attrs() {
    return new HashMap<>(0);
  }
}
