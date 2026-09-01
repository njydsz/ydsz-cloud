package com.njydsz.userinfo.server.trace;

import com.njydsz.common.util.id.TracerUtils;

/**
 * 链路追踪上下文（P1-10）。
 *
 * <p>统一委托 {@link TracerUtils}（ydsz-common-util）管理 traceId，底层支持 SkyWalking → RequestContext → MDC 多级降级。
 *
 * <p>在请求入口处：从请求头 {@code X-Trace-Id} 读取上游 traceId（网关/Sleuth 传入）， 不存在则本地生成 UUID。
 * traceId 同步写入 SLF4J MDC 与 RequestContextProxy， 使日志输出与指标标签均可关联同一链路。
 *
 * <p><b>与云顶规范的关系：</b>遵循编码规范 23.5.2 节「链路追踪必须使用 ydsz-common-util 的 TracerUtils」， 禁止业务模块自建链路追踪实现。
 * 原来自建 ThreadLocal + MDC 方案已迁移至 TracerUtils，全系统统一链路追踪组件。
 *
 * <p><b>注意：</b>异步线程不自动传递 ThreadLocal，需要异步场景时由调用方在 子线程入口显式调用 {@link #setTraceId(String)}（或接入线程池 TaskDecorator）。
 *
 * <p><b>兼容性说明：</b>本类保留原有静态方法签名，作为薄适配层，降低业务调用方迁移成本。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see TracerUtils 统一链路追踪工具类
 */
public final class TraceContext {

  /** MDC / ThreadLocal 中 traceId 的键名 */
  public static final String TRACE_ID_KEY = "traceId";

  /** 请求头名称 */
  public static final String TRACE_ID_HEADER = "X-Trace-Id";

  private TraceContext() {
    throw new UnsupportedOperationException("TraceContext is a utility class");
  }

  /**
   * 获取当前线程 traceId；不存在时返回 null。
   *
   * <p>委托 {@link TracerUtils#getTraceId()}，底层按 SkyWalking → RequestContext → MDC 顺序查找。
   *
   * @return traceId 或 null
   */
  public static String getTraceId() {
    String traceId = TracerUtils.getTraceId();
    return (traceId != null && !traceId.isEmpty()) ? traceId : null;
  }

  /**
   * 设置当前线程 traceId（同步写入 MDC 与 RequestContext）。
   *
   * @param traceId traceId；null 或空白时清除
   */
  public static void setTraceId(String traceId) {
    if (traceId == null || traceId.isBlank()) {
      clear();
      return;
    }
    TracerUtils.setTraceId(traceId);
  }

  /**
   * 获取或生成 traceId：优先使用传入值，否则生成 UUID。
   *
   * @param incoming 上游传入的 traceId（可为 null）
   * @return 最终使用的 traceId
   */
  public static String getOrGenerate(String incoming) {
    if (incoming != null && !incoming.isBlank()) {
      return incoming.trim();
    }
    return TracerUtils.generateTraceId();
  }

  /** 清除当前线程 traceId（请求结束时调用，防止线程池复用串号）。 */
  public static void clear() {
    TracerUtils.clear();
  }
}
