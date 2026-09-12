package com.njydsz.gateway.exception;

import java.net.ConnectException;
import java.util.concurrent.TimeoutException;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;
import reactor.core.publisher.Mono;

import com.njydsz.common.auth.exception.PermissionDeniedException;
import com.njydsz.common.base.webflux.WebFluxErrorUtils;
import com.njydsz.common.core.trace.TraceIdGenerator;
import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.exception.custom.SysException;
import com.njydsz.gateway.config.GatewayConstants;
import com.njydsz.gateway.config.GatewayErrorCode;

/**
 * 网关全局异常处理器。
 *
 * <p>实现 {@link WebExceptionHandler} 接口，拦截所有网关层异常并返回统一 JSON 错误响应。
 *
 * <p>仅处理响应尚未提交（body 未写出）的异常；已提交则原样抛出交由容器兜底。
 * 状态码 / 业务码 / 错误消息分别由 {@link #resolveHttpStatus}、{@link #resolveBizCode}、
 * {@link #resolveMessage} 解析。
 *
 * <p>错误响应通过 {@link WebFluxErrorUtils} 统一构建，与网关过滤器拒绝路径保持格式一致。
 *
 * <p>通过 {@code @Order(-2)} 确保优先于 Spring Boot 默认的 ErrorWebExceptionHandler。
 *
 * @since 26.09.01
 * @author ydsz-team
 */
@Slf4j
@Order(-2)
public class GatewayExceptionHandler implements WebExceptionHandler {

  /**
   * 处理网关层异常并返回统一 JSON 错误响应。
   *
   * @param exchange 服务器 Web 交换上下文
   * @param ex 待处理的异常
   * @return 写出错误响应后的完成信号 Mono
   */
  @Override
  public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
    ServerHttpResponse response = exchange.getResponse();
    if (response.isCommitted()) {
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

    boolean preferProblemJson =
        WebFluxErrorUtils.acceptsProblemJson(
            exchange.getRequest().getHeaders().getFirst(HttpHeaders.ACCEPT));

    log.warn(
        "[GatewayError] status={} bizCode={} traceId={} path={} error={}",
        httpStatus.value(),
        bizCode,
        traceId,
        exchange.getRequest().getURI().getPath(),
        ex.getClass().getSimpleName() + ": " + ex.getMessage());

    GatewayErrorCode errorCode = GatewayErrorCode.fromCode(bizCode);

    return WebFluxErrorUtils.buildErrorResponse(
        response, httpStatus.value(), String.valueOf(bizCode), message, traceId, preferProblemJson);
  }

  /**
   * 根据异常类型解析 HTTP 状态码。
   *
   * <p>扩展映射规则，覆盖认证授权异常和业务异常。
   *
   * @param ex 待处理的异常
   * @return 解析后的 HTTP 状态码
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
    if (ex instanceof ConnectException) {
      return HttpStatus.BAD_GATEWAY;
    }
    if (ex instanceof TimeoutException) {
      return HttpStatus.GATEWAY_TIMEOUT;
    }
    String className = ex.getClass().getSimpleName();
    if ("NotFoundException".equals(className)) {
      return HttpStatus.NOT_FOUND;
    }
    if (isAuthenticationException(className)) {
      return HttpStatus.UNAUTHORIZED;
    }
    return HttpStatus.INTERNAL_SERVER_ERROR;
  }

  /**
   * 判断是否为认证类异常（通过类名模式匹配兜底）。
   *
   * <p>覆盖 Spring Security 的 {@code AuthenticationException}、{@code
   * BadCredentialsException}、{@code JwtException} 等，以及自定义的认证失败异常。
   * 返回 401 便于前端统一拦截跳转登录页。
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
   * 根据 HTTP 状态码和异常类型映射业务错误码。
   *
   * <p>优先使用 {@link BusinessException#getCode()} 中的业务编码，非数字编码时按 HTTP 状态码 × 100 生成降级码。
   *
   * @param httpStatus HTTP 状态码
   * @param ex 待处理的异常
   * @return 业务错误码
   */
  private int resolveBizCode(HttpStatus httpStatus, Throwable ex) {
    if (ex instanceof BusinessException bizEx) {
      String code = bizEx.getCode();
      if (code != null && !code.isBlank()) {
        try {
          return Integer.parseInt(code);
        } catch (NumberFormatException e) {
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
   * 解析用户友好的错误消息。
   *
   * <p>对于 {@link BusinessException} 优先使用其自带的消息（已通过 i18n 解析），
   * 其他情况使用 {@link GatewayErrorCode} 的 i18n key。
   *
   * @param ex 待处理的异常
   * @param httpStatus HTTP 状态码
   * @return 错误消息
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
