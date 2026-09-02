package com.njydsz.common.core.constant;

/**
 * 公共模块 HTTP 请求头常量定义（核心层保留通用协议常量）。
 *
 * <p>仅定义项目通用的请求头名称（链路追踪、网络信息），标准 HTTP 头（如 {@code Content-Type}、{@code Authorization}）
 * 直接在代码中使用字符串字面量即可，无需在此定义常量。
 *
 * <p>约定：
 *
 * <ul>
 *   <li>统一使用 Title Case 风格（如 X-Access-Token）
 *   <li>集合类 header 默认使用 CSV（逗号分隔），也允许多 header 值
 *   <li>表级列规则使用分号分隔不同表（如 {@code table:col1,col2;table2:col3}）
 * </ul>
 *
 * <p>本模块定义以下<b>协议级/通用</b>常量：链踪（TRACE_ID/W3C_TRACEPARENT）、网络（X-Forwarded-For/X-Request-Id）、幂等键。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public final class HeaderConstants {

  private HeaderConstants() {
    throw new UnsupportedOperationException("Utility class");
  }

  /**
   * HTTP 标准授权头（RFC 7235 / RFC 6750）。
   *
   * <p>值 {@code Authorization}，用于承载 OAuth2 Bearer Token、Basic 等标准授权凭证。 统一项目中 Authorization 头的引用，消除
   * {@code TokenConstants} 与 {@code HeaderConstants} 双源重复定义。
   *
   * @since 26.09.01
   */
  public static final String AUTHORIZATION = "Authorization";

  // ============================== 通用协议级常量（核心层保留） ==============================

  /**
   * 幂等键。
   *
   * <p>客户端通过此 Header 传递幂等键，服务端据此保证操作幂等性。 参考 Stripe API 的 Idempotency-Key 设计。
   *
   * @since 26.09.01
   */
  public static final String IDEMPOTENCY_KEY = "X-Idempotency-Key";

  // ============================== 链路追踪 ==============================
  // 以下为核心层保留的协议级常量

  /**
   * 请求唯一标识 HTTP 头。
   *
   * <p>值为 {@code "X-Request-Id"}，由网关在请求入口自动生成并写入， 用于请求在全生命周期中的唯一标识与故障排查。 与 {@link #TRACE_ID_HEADER}
   * 的区别：X-Request-Id 由本系统产生， X-Trace-Id 兼容 SkyWalking / Jaeger 等外部链路追踪系统。
   *
   * @since 26.09.01
   */
  public static final String X_REQUEST_ID = "X-Request-Id";

  /**
   * 请求追踪 ID HTTP 头。
   *
   * <p>值为 {@code "X-Trace-Id"}，用于全链路请求追踪， 贯穿网关、服务间调用、日志记录等场景。
   */
  public static final String TRACE_ID_HEADER = "X-Trace-Id";

  /**
   * TraceId 在 SLF4J MDC 中的 key 名称。
   *
   * <p>日志框架通过此 key 从 MDC 中提取 traceId 注入日志输出格式。
   */
  public static final String MDC_TRACE_ID_KEY = "traceId";

  /**
   * W3C Trace Context 标准的 traceparent header 名称。
   *
   * <p>格式：{@code 00-{traceId}-{spanId}-01}，用于对接 SkyWalking/Jaeger/Zipkin 等主流分布式链路追踪系统。
   *
   * @since 26.09.01
   * @see <a href="https://www.w3.org/TR/trace-context/">W3C Trace Context</a>
   */
  public static final String W3C_TRACEPARENT = "traceparent";

  /**
   * W3C Trace Context 标准的 tracestate header 名称。
   *
   * <p>用于传递供应商特定的追踪上下文信息。
   *
   * @since 26.09.01
   * @see <a href="https://www.w3.org/TR/trace-context/">W3C Trace Context</a>
   */
  public static final String W3C_TRACESTATE = "tracestate";

  // ============================== 网络信息 ==============================
  // 以下为核心层保留的通用网络常量

  /**
   * 请求来源标识。
   *
   * <p>用于标识请求的来源渠道（如 PC Web / H5 / APP / 小程序）。
   */
  public static final String X_REQUEST_SOURCE = "X-Request-Source";

  /**
   * 请求来源 IP。
   *
   * <p>用于服务间透传客户端真实 IP。通常由网关/负载均衡写入； 若不存在，可由服务端根据 HttpServletRequest 获取并补齐。
   *
   * <p>区别于标准的 {@code X-Forwarded-For}（支持多段链路 IP）， 本系统约定使用单值，作为"客户端 IP"的透传载体。
   */
  public static final String X_FORWARDED_FOR = "X-Forwarded-For";
}
