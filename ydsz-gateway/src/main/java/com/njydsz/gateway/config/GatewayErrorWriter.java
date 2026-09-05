package com.njydsz.gateway.config;

import java.time.OffsetDateTime;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.core.trace.TraceIdGenerator;
import com.njydsz.common.json.YdszJson;

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
 *   <li>携带 RFC 5988 {@code Link} 帮助文档头
 * </ul>
 *
 * <p><b>设计约束：</b>本类为无状态静态工具，所有过滤器拒绝路径统一调用 {@link #write(ServerWebExchange, HttpStatus,
 * GatewayErrorCode, String, String)}，禁止再各自拼接 JSON。
 *
 * @since 26.09.01
 * @author ydsz-team
 */
@Slf4j
public final class GatewayErrorWriter {

  /** ProblemDetail 媒体类型（RFC 7807） */
  private static final MediaType PROBLEM_JSON_MEDIA_TYPE =
      MediaType.valueOf("application/problem+json");

  private GatewayErrorWriter() {
    throw new UnsupportedOperationException("Utility class");
  }

  /**
   * 写出统一错误响应（自动从 exchange 获取或生成 traceId）。
   *
   * <p>响应未提交时输出 JSON 错误体；已提交则直接完成（避免重复写响应导致的 IllegalStateException）。 此重载方法自动从请求头获取 {@code X-Trace-Id}，无需调用方手动传递。
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
   * 写出统一错误响应（显式指定 traceId）。
   *
   * <p>响应未提交时输出 JSON 错误体；已提交则直接完成（避免重复写响应导致的 IllegalStateException）。
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

    // Accept 协商：客户端请求 problem+json 时返回 RFC 7807 格式
    boolean preferProblemJson = prefersProblemJson(exchange.getRequest());

    YdszResponse<Void> body =
        buildErrorBody(httpStatus, errorCode, message, finalTraceId, preferProblemJson);

    response.setStatusCode(httpStatus);
    response
        .getHeaders()
        .setContentType(preferProblemJson ? PROBLEM_JSON_MEDIA_TYPE : MediaType.APPLICATION_JSON);
    response.getHeaders().add(GatewayConstants.HEADER_TRACE_ID, finalTraceId);

    // RFC 5988 Link 头指向错误文档
    String helpUrl = errorCode.getHelpUrl();
    if (helpUrl != null && !helpUrl.isBlank()) {
      response.getHeaders().add(HttpHeaders.LINK, "<" + helpUrl + ">; rel=\"help\"");
    }

    byte[] bytes = YdszJson.toJsonBytes(body);
    DataBuffer buffer = response.bufferFactory().wrap(bytes);
    return response.writeWith(Mono.just(buffer));
  }

  /**
   * 构建统一错误响应体。
   *
   * @param httpStatus HTTP 状态码
   * @param errorCode 网关业务错误码枚举
   * @param message 错误消息
   * @param traceId 链路追踪 ID
   * @param preferProblemJson 是否输出 RFC 7807 ProblemDetail 格式
   * @return 错误响应体
   */
  private static YdszResponse<Void> buildErrorBody(
      HttpStatus httpStatus,
      GatewayErrorCode errorCode,
      String message,
      String traceId,
      boolean preferProblemJson) {
    String bizCode = String.valueOf(errorCode.getCode());
    String helpUrl = errorCode.getHelpUrl();

    YdszResponse<Void> body = YdszResponse.error(bizCode, message);
    if (preferProblemJson) {
      // RFC 7807 ProblemDetail 扩展字段
      body.putExtension("type", helpUrl != null ? helpUrl : "https://docs.ydsz.com/errors/" + bizCode);
      body.putExtension("title", httpStatus.getReasonPhrase());
      body.putExtension("status", String.valueOf(httpStatus.value()));
      body.putExtension("instance", "");
      body.putExtension("timestamp", OffsetDateTime.now().toString());
    } else {
      // ydsz 标准格式（向后兼容）
      body.putExtension("help", helpUrl);
      body.putExtension(
          "type", helpUrl != null ? helpUrl : "https://docs.ydsz.com/errors/" + bizCode);
      body.putExtension("timestamp", OffsetDateTime.now().toString());
    }
    body.assignTraceId(traceId);
    return body;
  }

  /**
   * 判断请求是否优先接受 ProblemDetail 格式。
   *
   * @param request HTTP 请求
   * @return true=优先返回 ProblemDetail
   */
  private static boolean prefersProblemJson(ServerHttpRequest request) {
    String acceptHeader = request.getHeaders().getFirst(HttpHeaders.ACCEPT);
    if (acceptHeader == null || acceptHeader.isBlank()) {
      return false;
    }
    return acceptHeader.contains("application/problem+json");
  }
}
