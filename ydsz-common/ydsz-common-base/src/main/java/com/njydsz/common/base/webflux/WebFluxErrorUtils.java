package com.njydsz.common.base.webflux;

import java.time.OffsetDateTime;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import reactor.core.publisher.Mono;

import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.json.YdszJson;

/**
 * WebFlux 响应式栈统一错误响应工具类。
 *
 * <p>为 Spring WebFlux / Spring Cloud Gateway 等响应式场景提供统一的错误响应构建能力，
 * 输出格式与 {@link YdszResponse} 保持一致。
 *
 * <p><b>设计约束：</b>
 *
 * <ul>
 *   <li>仅依赖 spring-web（reactive API），不引入 servlet 依赖，可与 servlet 栈共存
 *   <li>所有方法均为无状态静态工具，线程安全
 * </ul>
 *
 * <p><b>使用示例：</b>
 *
 * <pre>{@code
 * // 在 WebExceptionHandler 或 Gateway 过滤器中：
 * return WebFluxErrorUtils.buildErrorResponse(
 *     response, 429, "42901", "请求过于频繁");
 * }</pre>
 *
 * @since 26.09.01
 * @author ydsz-team
 */
public final class WebFluxErrorUtils {

  /** RFC 7807 ProblemDetail 媒体类型 */
  private static final MediaType PROBLEM_JSON_MEDIA_TYPE =
      MediaType.valueOf("application/problem+json");

  private WebFluxErrorUtils() {
    throw new UnsupportedOperationException("Utility class");
  }

  /**
   * 构建并写出统一错误响应。
   *
   * <p>设置 HTTP 状态码、Content-Type（基于 Accept 头自动协商 RFC 7807 或标准 JSON）、
   * {@code X-Trace-Id} 响应头，并将 {@link YdszResponse} 序列化后写入响应体。
   *
   * <p>响应已提交时直接返回完成信号，避免重复写出导致的异常。
   *
   * @param response 服务器 HTTP 响应对象
   * @param status HTTP 状态码（如 429、500）
   * @param code 业务错误码（字符串形式，如 "42901"）
   * @param message 错误消息（i18n key 或具体文案）
   * @return 写出完成信号 Mono
   */
  public static Mono<Void> buildErrorResponse(
      ServerHttpResponse response, int status, String code, String message) {
    return buildErrorResponseInternal(response, status, code, message, null, false);
  }

  /**
   * 构建并写出统一错误响应（带 traceId）。
   *
   * <p>适合需要显式传递 traceId 的场景（如 Gateway 过滤器）。
   *
   * @param response 服务器 HTTP 响应对象
   * @param status HTTP 状态码
   * @param code 业务错误码
   * @param message 错误消息
   * @param traceId 链路追踪 ID
   * @return 写出完成信号 Mono
   */
  public static Mono<Void> buildErrorResponse(
      ServerHttpResponse response, int status, String code, String message, String traceId) {
    return buildErrorResponseInternal(response, status, code, message, traceId, false);
  }

  /**
   * 构建并写出统一错误响应（完整参数）。
   *
   * <p>支持 RFC 7807 ProblemDetail 格式协商和显式 traceId 注入。
   *
   * @param response 服务器 HTTP 响应对象
   * @param status HTTP 状态码
   * @param code 业务错误码
   * @param message 错误消息
   * @param traceId 链路追踪 ID（可为 null）
   * @param preferProblemJson 是否输出 RFC 7807 ProblemDetail 格式
   * @return 写出完成信号 Mono
   */
  public static Mono<Void> buildErrorResponse(
      ServerHttpResponse response,
      int status,
      String code,
      String message,
      String traceId,
      boolean preferProblemJson) {
    return buildErrorResponseInternal(response, status, code, message, traceId, preferProblemJson);
  }

  /**
   * 仅构建错误响应体（不写出），供调用方自行处理序列化。
   *
   * <p>适用于需要将错误体嵌入自定义响应结构的场景。
   *
   * @param code 业务错误码
   * @param message 错误消息
   * @param traceId 链路追踪 ID（可为 null）
   * @return 错误响应体
   */
  public static YdszResponse<Void> buildErrorBody(String code, String message, String traceId) {
    YdszResponse<Void> body = YdszResponse.error(code, message);
    if (traceId != null && !traceId.isBlank()) {
      body.assignTraceId(traceId);
    }
    body.putExtension("timestamp", OffsetDateTime.now().toString());
    body.putExtension("help", "https://docs.njydsz.com/errors/" + code);
    body.putExtension("type", "https://docs.njydsz.com/errors/" + code);
    return body;
  }

  /**
   * 判断给定 Accept 头是否优先接受 RFC 7807 ProblemDetail 格式。
   *
   * @param acceptHeader Accept 头值
   * @return true=优先返回 ProblemDetail
   */
  public static boolean acceptsProblemJson(String acceptHeader) {
    if (acceptHeader == null || acceptHeader.isBlank()) {
      return false;
    }
    return acceptHeader.contains("application/problem+json");
  }

  /**
   * 内部统一错误响应写出逻辑。
   *
   * @param response 服务器 HTTP 响应对象
   * @param status HTTP 状态码
   * @param code 业务错误码
   * @param message 错误消息
   * @param traceId 链路追踪 ID（可为 null）
   * @param preferProblemJson 是否输出 ProblemDetail 格式
   * @return 写出完成信号 Mono
   */
  private static Mono<Void> buildErrorResponseInternal(
      ServerHttpResponse response,
      int status,
      String code,
      String message,
      String traceId,
      boolean preferProblemJson) {
    if (response.isCommitted()) {
      return response.setComplete();
    }

    HttpStatus httpStatus = HttpStatus.valueOf(status);

    YdszResponse<Void> body = YdszResponse.error(code, message);
    if (traceId != null && !traceId.isBlank()) {
      body.assignTraceId(traceId);
    }
    body.putExtension("timestamp", OffsetDateTime.now().toString());
    String helpUrl = "https://docs.njydsz.com/errors/" + code;
    body.putExtension("type", helpUrl);

    if (preferProblemJson) {
      body.putExtension("title", httpStatus.getReasonPhrase());
      body.putExtension("status", String.valueOf(status));
      body.putExtension("instance", "");
    } else {
      body.putExtension("help", helpUrl);
    }

    response.setStatusCode(httpStatus);
    response
        .getHeaders()
        .setContentType(preferProblemJson ? PROBLEM_JSON_MEDIA_TYPE : MediaType.APPLICATION_JSON);

    if (traceId != null && !traceId.isBlank()) {
      response.getHeaders().add("X-Trace-Id", traceId);
    }

    byte[] bytes = YdszJson.toJsonBytes(body);
    DataBuffer buffer = response.bufferFactory().wrap(bytes);
    return response.writeWith(Mono.just(buffer));
  }
}
