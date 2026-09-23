package com.njydsz.gateway.config;

import org.springframework.http.server.reactive.ServerHttpRequest;

/**
 * 网关过滤器公共工具方法。
 *
 * <p>抽取多个 GlobalFilter 中重复出现的"剥离内部注入头 + 保留 traceId + 转发的 Accept-Language"
 * 请求变异逻辑，消除代码重复，符合 DRY 原则。
 *
 * <h3>典型用途</h3>
 *
 * <p>OPTIONS 预检请求、白名单路径放行等场景需要构建一个已剥离客户端伪造内部头、
 * 注入网关统一 traceId、透传 Accept-Language 的新请求对象时，统一调用
 * {@link #mutateRequestPreservingHeaders} 完成。
 *
 * @since 26.09.23
 * @author ydsz-team
 */
public final class GatewayFilterUtils {

  private GatewayFilterUtils() {
    throw new UnsupportedOperationException("Utility class");
  }

  /**
   * 构建剥离内部注入头、注入网关统一 traceId 和透传 Accept-Language 的新请求。
   *
   * <p>处理步骤：
   *
   * <ol>
   *   <li>剥离所有可能被客户端伪造的内部注入头（委托 {@link PathGuard#internalHeaders()}）
   *   <li>注入网关生成的统一 traceId 到请求头
   *   <li>透传客户端的 Accept-Language 头（若存在）；若缺失则使用默认值
   * </ol>
   *
   * <p>本方法为无状态纯函数，不修改原始请求，返回的新请求通过 {@link ServerHttpRequest#mutate()} 构建。
   *
   * @param original 原始 HTTP 请求（非 null）
   * @param traceId 网关生成的链路追踪 ID（非空）
   * @return 剥离内部注入头并注入统一 traceId 和 Accept-Language 的新请求 Builder
   */
  public static ServerHttpRequest.Builder mutateRequestPreservingHeaders(
      ServerHttpRequest original, String traceId) {
    ServerHttpRequest.Builder builder = original.mutate();
    stripInternalHeaders(builder);
    builder.headers(h -> h.set(GatewayConstants.HEADER_TRACE_ID, traceId));

    String acceptLang = original.getHeaders().getFirst("Accept-Language");
    if (acceptLang != null && !acceptLang.isEmpty()) {
      builder.headers(h -> h.set("Accept-Language", acceptLang));
    }
    return builder;
  }

  /**
   * 构建剥离内部注入头、注入网关统一 traceId 和指定 Accept-Language 的新请求。
   *
   * <p>与 {@link #mutateRequestPreservingHeaders(ServerHttpRequest, String)} 行为一致，
   * 但当客户端未传递 Accept-Language 时使用指定的默认值而非透传。
   *
   * @param original 原始 HTTP 请求（非 null）
   * @param traceId 网关生成的链路追踪 ID（非空）
   * @param defaultAcceptLanguage 客户端 Accept-Language 为空时使用的默认语言（如 {@code "zh-CN"}）
   * @return 剥离内部注入头并注入统一 traceId 和 Accept-Language 的新请求 Builder
   */
  public static ServerHttpRequest.Builder mutateRequestPreservingHeaders(
      ServerHttpRequest original, String traceId, String defaultAcceptLanguage) {
    ServerHttpRequest.Builder builder = original.mutate();
    stripInternalHeaders(builder);
    builder.headers(h -> h.set(GatewayConstants.HEADER_TRACE_ID, traceId));

    String acceptLang = original.getHeaders().getFirst("Accept-Language");
    String effectiveLang = (acceptLang != null && !acceptLang.isEmpty()) ? acceptLang : defaultAcceptLanguage;
    builder.headers(h -> h.set("Accept-Language", effectiveLang));
    return builder;
  }

  /**
   * 从请求 Builder 中剥离所有可能被客户端伪造的内部注入头。
   *
   * <p>内部注入头集合由 {@link PathGuard#internalHeaders()} 定义，包括 X-User-*、X-Internal-Sig、
   * X-Tenant-Id、X-Device-Type 等网关层注入的下游头，以及 X-Forwarded-For、X-Real-IP 等代理头。
   * 客户端传入的这些头必须在网关注入真实值之前删除，防止伪造。
   *
   * @param builder 请求 Builder
   */
  public static void stripInternalHeaders(ServerHttpRequest.Builder builder) {
    for (String name : PathGuard.internalHeaders()) {
      builder.headers(h -> h.remove(name));
    }
  }
}
