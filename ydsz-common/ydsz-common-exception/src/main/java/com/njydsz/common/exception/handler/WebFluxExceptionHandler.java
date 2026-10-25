package com.njydsz.common.exception.handler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.MessageSource;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;

import com.njydsz.common.exception.code.CoreExceptionCode;
import com.njydsz.common.exception.config.ExceptionProperties;
import com.njydsz.common.exception.core.ExceptionInfo;
import com.njydsz.common.exception.custom.AbstractYdszException;
import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.exception.custom.SysException;
import com.njydsz.common.exception.metrics.ExceptionMetrics;

/**
 * Spring WebFlux 全局异常处理器
 *
 * <p>处理 WebFlux 应用中的各类异常，与 MVC 异常处理器对应。 仅在 WebFlux 环境下自动装配。
 *
 * <p><b>职责：</b>
 *
 * <ul>
 *   <li>处理业务异常、系统异常等
 *   <li>统一返回 YdszResponse 或 ProblemDetail 格式（通过配置切换）
 *   <li>记录异常指标（Counter + Timer）
 *   <li>提取 traceId 用于链路追踪
 * </ul>
 *
 * @author ydsz-team
 * @since 26.10.01
 * @see BaseExceptionHandler
 * @see MvcExceptionHandler
 */
@Slf4j
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
  // CHECKSTYLE.OFF: RegexpSinglelineJava — 字符串常量（注解/反射类名），非代码引用
@ConditionalOnClass(name = "org.springframework.web.server.ServerWebExchange")
  // CHECKSTYLE.ON: RegexpSinglelineJava
@ConditionalOnProperty(
    prefix = "ydsz.exception",
    name = "global-handler-enabled",
    havingValue = "true",
    matchIfMissing = true)
public class WebFluxExceptionHandler extends BaseExceptionHandler {

  /** 系统错误兜底文案（i18n key 解析失败时使用，避免向客户端泄露内部异常细节） */
  private static final String DEFAULT_SYSTEM_ERROR_MESSAGE = "系统异常，请联系管理员";

  /** 非法参数兜底文案（i18n key 解析失败时使用，避免泄露内部异常细节） */
  private static final String DEFAULT_ILLEGAL_ARGUMENT_MESSAGE = "非法参数";

  /** 资源不存在兜底文案（i18n key 解析失败时使用） */
  private static final String DEFAULT_RESOURCE_NOT_FOUND_MESSAGE = "资源不存在";

  /** 客户端请求错误兜底文案（i18n key 解析失败时使用，掩码异常原始描述） */
  private static final String DEFAULT_CLIENT_ERROR_MESSAGE = "请求无法处理";

  private final MessageSource messageSource;

  /**
   * 构造 WebFlux 全局异常处理器
   *
   * <p>使用 {@link ObjectProvider} 包装可选依赖，确保在组件扫描阶段也能正常实例化。
   *
   * @param environment Spring 环境对象
   * @param messageSource 国际化消息源
   * @param exceptionMetricsProvider 异常指标统计器提供器（可选）
   * @param propertiesProvider 异常模块配置属性提供器（可选）
   */
  public WebFluxExceptionHandler(
      Environment environment,
      MessageSource messageSource,
      ObjectProvider<ExceptionMetrics> exceptionMetricsProvider,
      ObjectProvider<ExceptionProperties> propertiesProvider) {
    super(environment);
    this.messageSource = messageSource;
    setMessageSource(messageSource);
    setExceptionMetrics(environment, exceptionMetricsProvider.getIfAvailable());
    setExceptionProperties(environment, propertiesProvider.getIfAvailable());
  }

  @Override
  protected String getLogPrefix() {
    return "【WebFlux】";
  }

  // ============================ 异常处理方法 ============================

  /**
   * 处理业务异常（动态 HTTP 状态码）
   *
   * @param e 异常对象
   * @param exchange WebFlux 请求上下文
   * @return 处理结果
   */
  @ExceptionHandler(BusinessException.class)
  public Object handleBusinessException(BusinessException e, ServerWebExchange exchange) {
    recordMetrics(e);
    log.warn(
        "{}业务异常 | 路径: {} | 错误码: {} | 消息: {}",
        getLogPrefix(),
        exchange.getRequest().getPath().value(),
        e.getCode(),
        e.getMessage(),
        e);

    String traceId = extractTraceId(exchange);
    return buildResponse(e, exchange.getRequest().getPath().value(), traceId);
  }

  /**
   * 处理系统异常
   *
   * @param e 异常对象
   * @param exchange WebFlux 请求上下文
   * @return 处理结果
   */
  @ExceptionHandler(SysException.class)
  @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
  public Object handleSysException(SysException e, ServerWebExchange exchange) {
    recordMetrics(e);
    log.error(
        "{}系统异常 | 路径: {} | 错误码: {} | 消息: {}",
        getLogPrefix(),
        exchange.getRequest().getPath().value(),
        e.getCode(),
        e.getMessage(),
        e);

    return buildResponse(e, exchange.getRequest().getPath().value(), extractTraceId(exchange));
  }

  /**
   * 处理其他 YDSZ 异常（兜底，捕获所有 AbstractYdszException 子类）
   *
   * @param e 异常对象
   * @param exchange WebFlux 请求上下文
   * @return 处理结果
   */
  @ExceptionHandler(AbstractYdszException.class)
  public Object handleAbstractYdszException(AbstractYdszException e, ServerWebExchange exchange) {
    recordMetrics(e);
    log.warn(
        "{}异常 | 路径: {} | 错误码: {} | 消息: {} | 类型: {}",
        getLogPrefix(),
        exchange.getRequest().getPath().value(),
        e.getCode(),
        e.getMessage(),
        e.getClass().getSimpleName(),
        e);

    return buildResponse(e, exchange.getRequest().getPath().value(), extractTraceId(exchange));
  }

  /**
   * 处理非法参数异常
   *
   * @param e 异常对象
   * @param exchange WebFlux 请求上下文
   * @return 处理结果
   */
  @ExceptionHandler(IllegalArgumentException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public Object handleIllegalArgumentException(
      IllegalArgumentException e, ServerWebExchange exchange) {
    recordMetrics(e);
    log.error(
        "{}非法参数异常 | 路径: {} | 消息: {}",
        getLogPrefix(),
        exchange.getRequest().getPath().value(),
        e.getMessage(),
        e);

    // 与 MVC 处理器行为对齐：按请求 Locale 解析 i18n 文案，原始异常消息仅保留在日志中
    String message =
        resolveMessage(
            CoreExceptionCode.ILLEGAL_ARGUMENT.getKey(), null, DEFAULT_ILLEGAL_ARGUMENT_MESSAGE);
    return buildStandardErrorResponse(
        CoreExceptionCode.ILLEGAL_ARGUMENT.getCode(),
        CoreExceptionCode.ILLEGAL_ARGUMENT.getKey(),
        message,
        HttpStatus.BAD_REQUEST.value(),
        exchange.getRequest().getPath().value());
  }

  /**
   * 处理非法状态异常
   *
   * <p>IllegalStateException 属于系统级异常（非业务异常），统一返回 SYSTEM_ERROR， 避免暴露内部状态信息。
   *
   * @param e 异常对象
   * @param exchange WebFlux 请求上下文
   * @return 处理结果
   */
  @ExceptionHandler(IllegalStateException.class)
  @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
  public Object handleIllegalStateException(IllegalStateException e, ServerWebExchange exchange) {
    recordMetrics(e);
    log.error(
        "{}非法状态异常 | 路径: {} | 消息: {}",
        getLogPrefix(),
        exchange.getRequest().getPath().value(),
        e.getMessage(),
        e);

    return buildSystemErrorResponse(e, exchange);
  }

  /**
   * 处理空指针异常
   *
   * @param e 异常对象
   * @param exchange WebFlux 请求上下文
   * @return 处理结果
   */
  @ExceptionHandler(NullPointerException.class)
  @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
  public Object handleNullPointerException(NullPointerException e, ServerWebExchange exchange) {
    recordMetrics(e);
    log.error(
        "{}空指针异常 | 路径: {} | 消息: {}",
        getLogPrefix(),
        exchange.getRequest().getPath().value(),
        e.getMessage(),
        e);

    return buildSystemErrorResponse(e, exchange);
  }

  /**
   * 处理所有未捕获的异常
   *
   * @param e 异常对象
   * @param exchange WebFlux 请求上下文
   * @return 处理结果
   */
  @ExceptionHandler(Exception.class)
  @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
  public Object handleException(Exception e, ServerWebExchange exchange) {
    recordMetrics(e);
    log.error(
        "{}系统异常 | 路径: {} | 类型: {} | 消息: {}",
        getLogPrefix(),
        exchange.getRequest().getPath().value(),
        e.getClass().getName(),
        e.getMessage(),
        e);

    return buildSystemErrorResponse(e, exchange);
  }

  /**
   * 处理携带 HTTP 状态码的响应异常（路由未命中、方法不支持等）。
   *
   * <p>WebFlux 的 DispatcherHandler 在无匹配路由或静态资源缺失时抛出 {@link ResponseStatusException}
   * （含其子类 NoResourceFoundException）。若不单独处理，会被兜底的 {@code Exception} 处理器降级为
   * 500 + SYSTEM_ERROR，既掩盖真实状态码，也让探活/扫描流量污染系统异常指标。
   *
   * @param e 异常对象
   * @param exchange WebFlux 请求上下文
   * @return 与原始状态码一致的统一错误响应
   */
  @ExceptionHandler(ResponseStatusException.class)
  public ResponseEntity<Object> handleResponseStatusException(
      ResponseStatusException e, ServerWebExchange exchange) {
    recordMetrics(e);
    String path = exchange.getRequest().getPath().value();
    HttpStatusCode status = e.getStatusCode();
    CoreExceptionCode errorCode = mapResponseStatus(status);

    if (status.is5xxServerError()) {
      log.error(
          "{}响应状态异常 | 路径: {} | 状态码: {} | 错误码: {} | 消息: {}",
          getLogPrefix(),
          path,
          status.value(),
          errorCode.getCode(),
          e.getMessage(),
          e);
    } else {
      log.warn(
          "{}响应状态异常 | 路径: {} | 状态码: {} | 错误码: {}",
          getLogPrefix(),
          path,
          status.value(),
          errorCode.getCode());
    }

    boolean notFound = status.value() == HttpStatus.NOT_FOUND.value();
    String message =
        notFound
            ? resolveMessage(
                "resource.not.found.detail",
                new Object[] {path},
                DEFAULT_RESOURCE_NOT_FOUND_MESSAGE)
            : resolveMessage(
                errorCode.getKey(),
                null,
                status.is4xxClientError()
                    ? DEFAULT_CLIENT_ERROR_MESSAGE
                    : DEFAULT_SYSTEM_ERROR_MESSAGE);

    Object body =
        buildStandardErrorResponse(
            errorCode.getCode(),
            errorCode.getKey(),
            message,
            status.value(),
            path,
            errorCode.getLevel());
    return ResponseEntity.status(status).body(body);
  }

  /**
   * 将 HTTP 状态码映射为平台错误码。
   *
   * @param status 原始 HTTP 状态码
   * @return 对应的核心异常码（未识别时按 4xx/5xx 归类兜底）
   */
  private static CoreExceptionCode mapResponseStatus(HttpStatusCode status) {
    HttpStatus resolved = HttpStatus.resolve(status.value());
    if (resolved == null) {
      return status.is4xxClientError()
          ? CoreExceptionCode.INVALID_REQUEST_FORMAT
          : CoreExceptionCode.INTERNAL_ERROR;
    }
    return switch (resolved) {
      case NOT_FOUND -> CoreExceptionCode.RESOURCE_NOT_FOUND;
      case METHOD_NOT_ALLOWED -> CoreExceptionCode.METHOD_NOT_ALLOWED;
      case CONFLICT -> CoreExceptionCode.CONFLICT;
      case CONTENT_TOO_LARGE -> CoreExceptionCode.FILE_SIZE_EXCEEDED;
      case TOO_MANY_REQUESTS -> CoreExceptionCode.RESOURCE_EXHAUSTED;
      case SERVICE_UNAVAILABLE -> CoreExceptionCode.SERVICE_UNAVAILABLE;
      case BAD_GATEWAY -> CoreExceptionCode.BAD_GATEWAY;
      case GATEWAY_TIMEOUT -> CoreExceptionCode.GATEWAY_TIMEOUT;
      case INTERNAL_SERVER_ERROR -> CoreExceptionCode.SYSTEM_ERROR;
      default -> status.is4xxClientError()
          ? CoreExceptionCode.INVALID_REQUEST_FORMAT
          : CoreExceptionCode.INTERNAL_ERROR;
    };
  }

  /**
   * 构建系统级错误响应（掩码内部异常细节）。
   *
   * <p>与 MVC 处理器行为对齐：仅返回 i18n 的 system.error 文案， 原始异常消息仅保留在日志与堆栈详情（dev/test）中，避免生产环境信息泄露。
   *
   * @param e 原始异常
   * @param exchange WebFlux 请求上下文
   * @return 统一错误响应
   */
  private Object buildSystemErrorResponse(Throwable e, ServerWebExchange exchange) {
    String message =
        resolveMessage(CoreExceptionCode.SYSTEM_ERROR.getKey(), null, DEFAULT_SYSTEM_ERROR_MESSAGE);
    ExceptionInfo info =
        buildExceptionInfo(e, exchange.getRequest().getPath().value(), extractTraceId(exchange));
    info.setCode(CoreExceptionCode.SYSTEM_ERROR.getCode());
    info.setMessage(message);
    return errorResponse(
        CoreExceptionCode.SYSTEM_ERROR.getCode(), message, includeExceptionInfo() ? info : null);
  }
}
