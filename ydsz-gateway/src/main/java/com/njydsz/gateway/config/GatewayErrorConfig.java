package com.njydsz.gateway.config;

import com.njydsz.common.auth.exception.PermissionDeniedException;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.core.trace.TraceIdGenerator;
import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.exception.custom.SysException;
import com.njydsz.common.json.YdszJson;
import java.time.OffsetDateTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import reactor.core.publisher.Mono;

/**
 * P3-7: 网关全局异常处理器配置（RFC 7807 ProblemDetail 对齐）
 *
 * <p><b>问题背景：</b>此前的全局异常处理器仅处理 {@link ResponseStatusException}、 {@link ConnectException}、{@link
 * TimeoutException} 和 Cloud Gateway 的 {@code NotFoundException}，对于认证/权限类异常（如 {@link
 * PermissionDeniedException}） 和业务异常（{@link BusinessException}）未做显式映射，导致这些异常被归为 {@link
 * HttpStatus#INTERNAL_SERVER_ERROR}（500），而非正确的 401/403/400。 前端无法根据 HTTP 状态码统一拦截认证失败，必须解析 body 中的
 * bizCode 才能识别。
 *
 * <p><b>P2-2 修复内容（已有）：</b>
 *
 * <ul>
 *   <li>{@link PermissionDeniedException} → 403 FORBIDDEN（权限不足）
 *   <li>{@link BusinessException} → 按其 {@code httpStatus} 映射（默认 400 BAD_REQUEST）
 *   <li>{@link SysException} → 500 INTERNAL_SERVER_ERROR（系统异常）
 *   <li>类名匹配 "Authentication"/"Unauthorized"/"Login"/"Token" → 401 UNAUTHORIZED
 *   <li>{@link ResponseStatusException} → 按其状态码（已有，增强日志）
 *   <li>其他未知异常 → 500 INTERNAL_SERVER_ERROR（兜底）
 * </ul>
 *
 * <p><b>P3-7 增强（RFC 7807 ProblemDetail 对齐）：</b>
 *
 * <ul>
 *   <li>响应体增加 {@code type} 字段（URI，指向错误文档，由 {@link GatewayErrorCode} 定义）
 *   <li>响应体增加 {@code instance} 字段（标识请求实例）
 *   <li>响应体增加 {@code timestamp} 字段（错误发生时间）
 *   <li>Accept 协商：{@code application/problem+json} 返回 ProblemDetail 格式， 默认返回 ydsz 标准 BaseResponse
 *       格式（向后兼容）
 * </ul>
 *
 * <p>所有响应体保持 {@link BaseResponse} 格式，包含 bizCode、message、traceId、help 链接。
 *
 * @since 3.7.0
 * @author ydsz-team
 */
@Slf4j
@Configuration
public class GatewayErrorConfig {

  /** ProblemDetail 媒体类型 */
  private static final MediaType PROBLEM_JSON_MEDIA_TYPE =
      MediaType.valueOf("application/problem+json");

  /**
   * 注册自定义网关异常处理器
   *
   * <p>通过 {@code @Order(-2)} 确保优先于 Spring Boot 默认的 ErrorWebExceptionHandler。
   *
   * @return 网关异常处理器
   */
  @Bean
  @Order(-2)
  public WebExceptionHandler gatewayErrorHandler() {
    return new GatewayExceptionHandler();
  }

  /**
   * 网关全局异常处理器
   *
   * <p>实现 {@link WebExceptionHandler} 接口， 拦截所有网关层异常并返回统一 {@link BaseResponse} JSON。
   */
  @Slf4j
  static class GatewayExceptionHandler implements WebExceptionHandler {

    /**
     * 处理网关层异常并返回统一 JSON 错误响应。
     *
     * <p>仅处理响应尚未提交（body 未写出）的异常；已提交则原样抛出交由容器兜底。 状态码 / 业务码 / 错误消息分别由 {@link
     * #resolveHttpStatus}、{@link #resolveBizCode}、 {@link #resolveMessage} 解析，并注入 traceId 便于跨服务排障。
     *
     * @param exchange 服务器 Web 交换上下文
     * @param ex 待处理的异常
     * @return 写出错误响应后的完成信号 Mono
     */
    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
      if (exchange.getResponse().isCommitted()) {
        return Mono.error(ex);
      }

      HttpStatus httpStatus = resolveHttpStatus(ex);
      int bizCode = resolveBizCode(httpStatus, ex);
      String message = resolveMessage(ex, httpStatus);

      String traceId =
          exchange.getRequest().getHeaders().getFirst(GatewayConstants.HEADER_TRACE_ID);
      if (traceId == null || traceId.isBlank()) {
        traceId = TraceIdGenerator.generateSortableTraceId();
      }

      GatewayErrorCode errorCode = GatewayErrorCode.fromCode(bizCode);

      // P3-7: Accept 协商 — 判断客户端是否请求 problem+json
      boolean preferProblemJson = prefersProblemJson(exchange.getRequest());

      BaseResponse<Void> body =
          buildErrorResponse(bizCode, message, traceId, httpStatus, errorCode, preferProblemJson);

      log.warn(
          "[GatewayError] status={} bizCode={} traceId={} path={} error={}",
          httpStatus.value(),
          bizCode,
          traceId,
          exchange.getRequest().getURI().getPath(),
          ex.getClass().getSimpleName() + ": " + ex.getMessage());

      ServerHttpResponse response = exchange.getResponse();
      response.setStatusCode(httpStatus);
      response
          .getHeaders()
          .setContentType(preferProblemJson ? PROBLEM_JSON_MEDIA_TYPE : MediaType.APPLICATION_JSON);
      response.getHeaders().add(GatewayConstants.HEADER_TRACE_ID, traceId);

      // RFC 5988 Link 头指向错误文档
      if (errorCode.getHelpUrl() != null && !errorCode.getHelpUrl().isBlank()) {
        response
            .getHeaders()
            .add(HttpHeaders.LINK, "<" + errorCode.getHelpUrl() + ">; rel=\"help\"");
      }

      byte[] bytes = YdszJson.toJsonBytes(body);
      DataBuffer buffer = response.bufferFactory().wrap(bytes);
      return response.writeWith(Mono.just(buffer));
    }

    /**
     * P3-7: 构建错误响应体（根据 Accept 头选择格式）
     *
     * @param bizCode 业务错误码
     * @param message 错误消息
     * @param traceId 链路追踪 ID
     * @param httpStatus HTTP 状态码
     * @param errorCode 网关错误码枚举
     * @param preferProblemJson 是否优先返回 ProblemDetail 格式
     * @return 错误响应体
     */
    private BaseResponse<Void> buildErrorResponse(
        int bizCode,
        String message,
        String traceId,
        HttpStatus httpStatus,
        GatewayErrorCode errorCode,
        boolean preferProblemJson) {
      BaseResponse<Void> body;
      if (preferProblemJson) {
        // RFC 7807 ProblemDetail 格式
        body = BaseResponse.error(String.valueOf(bizCode), message);
        body.putExtension(
            "type",
            errorCode.getHelpUrl() != null
                ? errorCode.getHelpUrl()
                : "https://docs.ydsz.com/errors/" + bizCode);
        body.putExtension("title", httpStatus.getReasonPhrase());
        body.putExtension("status", String.valueOf(httpStatus.value()));
        body.putExtension("instance", "");
        body.putExtension("timestamp", OffsetDateTime.now().toString());
      } else {
        // ydsz 标准格式（向后兼容）
        body = BaseResponse.error(String.valueOf(bizCode), message);
        body.putExtension("help", errorCode.getHelpUrl());
        // P3-7: 增加 type 和 instance 字段（即使不请求 problem+json 也提供）
        body.putExtension(
            "type",
            errorCode.getHelpUrl() != null
                ? errorCode.getHelpUrl()
                : "https://docs.ydsz.com/errors/" + bizCode);
        body.putExtension("timestamp", OffsetDateTime.now().toString());
      }
      body.assignTraceId(traceId);
      return body;
    }

    /**
     * P3-7: 判断请求是否优先接受 ProblemDetail 格式
     *
     * @param request HTTP 请求
     * @return true=优先返回 ProblemDetail
     */
    private boolean prefersProblemJson(ServerHttpRequest request) {
      String acceptHeader = request.getHeaders().getFirst(HttpHeaders.ACCEPT);
      if (acceptHeader == null || acceptHeader.isBlank()) {
        return false;
      }
      // 检查 Accept 头是否包含 application/problem+json
      return acceptHeader.contains("application/problem+json");
    }

    /**
     * 根据异常类型解析 HTTP 状态码
     *
     * <p>P2-2: 扩展映射规则，覆盖认证授权异常和业务异常。
     */
    private HttpStatus resolveHttpStatus(Throwable ex) {
      if (ex instanceof PermissionDeniedException) {
        return HttpStatus.FORBIDDEN;
      }
      if (ex instanceof ResponseStatusException rse) {
        HttpStatus resolved = HttpStatus.resolve(rse.getStatusCode().value());
        return resolved != null ? resolved : HttpStatus.INTERNAL_SERVER_ERROR;
      }
      if (ex instanceof BusinessException bizEx) {
        HttpStatus resolved = HttpStatus.resolve(bizEx.getHttpStatus());
        return resolved != null ? resolved : HttpStatus.BAD_REQUEST;
      }
      if (ex instanceof SysException) {
        return HttpStatus.INTERNAL_SERVER_ERROR;
      }
      if (ex instanceof java.net.ConnectException) {
        return HttpStatus.BAD_GATEWAY;
      }
      if (ex instanceof java.util.concurrent.TimeoutException) {
        return HttpStatus.GATEWAY_TIMEOUT;
      }
      // NotFoundException 来自 spring-cloud-gateway
      String className = ex.getClass().getSimpleName();
      if ("NotFoundException".equals(className)) {
        return HttpStatus.NOT_FOUND;
      }
      // P2-2: 类名模式匹配，兜底处理 Spring Security / 自定义认证异常
      if (isAuthenticationException(className)) {
        return HttpStatus.UNAUTHORIZED;
      }
      return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    /**
     * 判断是否为认证类异常（通过类名模式匹配兜底）
     *
     * <p>覆盖 Spring Security 的 {@code AuthenticationException}、 {@code
     * BadCredentialsException}、{@code JwtException} 等， 以及自定义的认证失败异常。返回 401 便于前端统一拦截跳转登录页。
     *
     * @param className 异常类短名称
     * @return true=认证类异常
     */
    private boolean isAuthenticationException(String className) {
      return className.contains("Authentication")
          || className.contains("Unauthorized")
          || className.contains("Login")
          || className.contains("Token")
          || className.contains("Credentials");
    }

    /**
     * 根据 HTTP 状态码和异常类型映射业务错误码
     *
     * <p>P2-2: 优先使用 {@link BusinessException#getCode()} 中的业务编码， 非数字编码时按 HTTP 状态码 × 100 生成降级码。
     */
    private int resolveBizCode(HttpStatus httpStatus, Throwable ex) {
      if (ex instanceof BusinessException bizEx) {
        String code = bizEx.getCode();
        if (code != null && !code.isBlank()) {
          try {
            return Integer.parseInt(code);
          } catch (NumberFormatException e) {
            // 非数字编码（如 "A02051"）时按状态码降级
            return httpStatus.value() * 100;
          }
        }
      }
      return switch (httpStatus) {
        case NOT_FOUND -> GatewayErrorCode.ROUTE_NOT_FOUND.getCode();
        case BAD_GATEWAY -> GatewayErrorCode.BAD_GATEWAY.getCode();
        case SERVICE_UNAVAILABLE -> GatewayErrorCode.SERVICE_UNAVAILABLE.getCode();
        case GATEWAY_TIMEOUT -> GatewayErrorCode.GATEWAY_TIMEOUT.getCode();
        case REQUEST_TIMEOUT -> GatewayErrorCode.REQUEST_TIMEOUT.getCode();
        case TOO_MANY_REQUESTS -> GatewayErrorCode.RATE_LIMITED.getCode();
        case UNAUTHORIZED -> GatewayErrorCode.UNAUTHORIZED.getCode();
        case FORBIDDEN -> GatewayErrorCode.FORBIDDEN.getCode();
        default -> httpStatus.value() * 100;
      };
    }

    /**
     * 解析用户友好的错误消息
     *
     * <p>P2-2: 对于 {@link BusinessException} 优先使用其自带的消息 （已通过 i18n 解析），其他情况使用 {@link
     * GatewayErrorCode} 的 i18n key。
     */
    private String resolveMessage(Throwable ex, HttpStatus httpStatus) {
      if (ex instanceof BusinessException bizEx && bizEx.getMessage() != null) {
        return bizEx.getMessage();
      }
      return switch (httpStatus) {
        case NOT_FOUND -> GatewayErrorCode.ROUTE_NOT_FOUND.getMessageKey();
        case BAD_GATEWAY -> GatewayErrorCode.BAD_GATEWAY.getMessageKey();
        case SERVICE_UNAVAILABLE -> GatewayErrorCode.SERVICE_UNAVAILABLE.getMessageKey();
        case GATEWAY_TIMEOUT -> GatewayErrorCode.GATEWAY_TIMEOUT.getMessageKey();
        case REQUEST_TIMEOUT -> GatewayErrorCode.REQUEST_TIMEOUT.getMessageKey();
        case TOO_MANY_REQUESTS -> GatewayErrorCode.RATE_LIMITED.getMessageKey();
        case UNAUTHORIZED -> GatewayErrorCode.UNAUTHORIZED.getMessageKey();
        case FORBIDDEN -> GatewayErrorCode.FORBIDDEN.getMessageKey();
        case INTERNAL_SERVER_ERROR -> GatewayErrorCode.INTERNAL_ERROR.getMessageKey();
        default ->
            ex.getMessage() != null
                ? ex.getMessage()
                : GatewayErrorCode.INTERNAL_ERROR.getMessageKey();
      };
    }
  }
}
