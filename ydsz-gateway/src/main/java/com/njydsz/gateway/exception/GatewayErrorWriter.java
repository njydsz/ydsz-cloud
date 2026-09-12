package com.njydsz.gateway.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import com.njydsz.common.base.webflux.WebFluxErrorUtils;
import com.njydsz.common.core.trace.TraceIdGenerator;
import com.njydsz.gateway.config.GatewayConstants;
import com.njydsz.gateway.config.GatewayErrorCode;

/**
 * 网关统一错误响应写出器（P0-D1）。
 *
 * <p>收编所有过滤器拒绝路径的错误响应构建逻辑（鉴权 / 限流 / IP 控制 / Payload 校验 / API Key /
 * Authorization / WebSocket），保证错误响应格式全局一致：
 *
 * <ul>
 *   <li>HTTP 状态码 + 5 位业务码（{@link GatewayErrorCode}）双轨输出
 *   <li>响应头携带 {@code X-Trace-Id}，便于跨服务排障
 *   <li>按 {@code Accept} 头协商返回 RFC 7807 ProblemDetail（{@code application/problem+json}）或 ydsz 标准 JSON
 * </ul>
 *
 * <p>内部委托 {@link WebFluxErrorUtils} 构建并写出错误响应，维护统一的错误体格式。
 *
 * <p><b>设计约束：</b>本类为无状态静态工具，所有过滤器拒绝路径统一调用
 * {@link #write(ServerWebExchange, HttpStatus, GatewayErrorCode, String, String)}，
 * 禁止再各自拼接 JSON。
 *
 * @since 26.09.01
 * @author ydsz-team
 */
@Slf4j
public final class GatewayErrorWriter {

  private GatewayErrorWriter() {
    throw new UnsupportedOperationException("Utility class");
  }

  /**
   * 写出统一错误响应（自动从 exchange 获取或生成 traceId）。
   *
   * <p>响应未提交时输出 JSON 错误体；已提交则直接完成（避免重复写响应导致的 IllegalStateException）。
   * 此重载方法自动从请求头获取 {@code X-Trace-Id}，无需调用方手动传递。
   *
   * @param exchange 服务器 Web 交换上下文
   * @param httpStatus HTTP 状态码
   * @param errorCode 网关业务错误码枚举
   * @param message 错误消息（i18n key 或具体文案）
   * @return 写出完成信号 Mono
   */
  public static Mono<Void> write(
      ServerWebExchange exchange,
      HttpStatus httpStatus,
      GatewayErrorCode errorCode,
      String message) {
    String traceId = exchange.getRequest().getHeaders().getFirst(GatewayConstants.HEADER_TRACE_ID);
    return write(exchange, httpStatus, errorCode, message, traceId);
  }

  /**
   * 写出已完全下线（Gone）的错误响应。
   *
   * <p>用于客户端请求已被彻底移除的 API 阶段（Sunset 后）。当前本任务仅实现弃用警告（200 + Header），
   * 不做拒绝。此方法留作 Sunset 阶段升级时使用。
   *
   * <p>当前阶段方法保留但不调用（deprecated APIs 仍返回 200 + Deprecation 头），当 Sunset
   * 日期到来时切换至此方法即可。
   *
   * @param exchange 服务器 Web 交换上下文
   * @param message 错误消息（如 "此 API 已于 2026-12-31 永久下线，请迁移至 v2"）
   * @param removalDate RFC 1123 HTTP-Date 格式的正式下线日期
   * @param successorPath 替代版本的完整路径（如 /api/v2/message/send）
   * @return 写出完成信号 Mono
   */
  public static Mono<Void> writeApiGone(
      ServerWebExchange exchange,
      String message,
      String removalDate,
      String successorPath) {
    ServerHttpResponse response = exchange.getResponse();
    if (response.isCommitted()) {
      return response.setComplete();
    }

    String traceId =
        exchange.getRequest().getHeaders().getFirst(GatewayConstants.HEADER_TRACE_ID);
    String finalTraceId =
        (traceId == null || traceId.isBlank())
            ? TraceIdGenerator.generateSortableTraceId()
            : traceId;

    // 构建 Sunset + Link 头
    if (removalDate != null && !removalDate.isBlank()) {
      response.getHeaders().add("Sunset", removalDate);
    }
    if (successorPath != null && !successorPath.isBlank()) {
      response
          .getHeaders()
          .add(HttpHeaders.LINK, "<" + successorPath + ">; rel=\"successor-version\"");
    }

    // 使用标准错误输出写出 410 响应
    return write(exchange, HttpStatus.GONE, GatewayErrorCode.SERVICE_UNAVAILABLE, message, finalTraceId);
  }

  /**
   * 写出统一错误响应（显式指定 traceId）。
   *
   * <p>响应未提交时输出 JSON 错误体；已提交则直接完成（避免重复写响应导致的 IllegalStateException）。
   * 内部委托 {@link WebFluxErrorUtils} 完成 Accept 协商、Body 构建与写出。
   *
   * @param exchange 服务器 Web 交换上下文
   * @param httpStatus HTTP 状态码
   * @param errorCode 网关业务错误码枚举
   * @param message 错误消息（i18n key 或具体文案）
   * @param traceId 链路追踪 ID（为空时自动生成）
   * @return 写出完成信号 Mono
   */
  public static Mono<Void> write(
      ServerWebExchange exchange,
      HttpStatus httpStatus,
      GatewayErrorCode errorCode,
      String message,
      String traceId) {
    ServerHttpResponse response = exchange.getResponse();
    if (response.isCommitted()) {
      return response.setComplete();
    }

    String finalTraceId = (traceId == null || traceId.isBlank())
        ? TraceIdGenerator.generateSortableTraceId()
        : traceId;

    boolean preferProblemJson =
        WebFluxErrorUtils.acceptsProblemJson(
            exchange.getRequest().getHeaders().getFirst(HttpHeaders.ACCEPT));

    // RFC 5988 Link 头指向错误文档
    String helpUrl = errorCode.getHelpUrl();
    if (helpUrl != null && !helpUrl.isBlank()) {
      response.getHeaders().add(HttpHeaders.LINK, "<" + helpUrl + ">; rel=\"help\"");
    }

    return WebFluxErrorUtils.buildErrorResponse(
        response,
        httpStatus.value(),
        String.valueOf(errorCode.getCode()),
        message,
        finalTraceId,
        preferProblemJson);
  }
}
