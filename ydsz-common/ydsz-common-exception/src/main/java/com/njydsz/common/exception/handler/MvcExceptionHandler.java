package com.njydsz.common.exception.handler;

import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.MessageSource;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.NoHandlerFoundException;

import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.exception.batch.BatchBusinessException;
import com.njydsz.common.exception.code.CoreExceptionCode;
import com.njydsz.common.exception.config.ExceptionProperties;
import com.njydsz.common.exception.core.ExceptionInfo;
import com.njydsz.common.exception.custom.AbstractYdszException;
import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.exception.custom.SysException;
import com.njydsz.common.exception.metrics.ExceptionMetrics;

/**
 * Spring MVC 全局异常处理器（非 Validation 部分）
 *
 * <p>处理业务异常、系统异常等通用异常。 Validation 相关异常处理由 {@link ValidationExceptionHandler} 负责（仅在
 * jakarta.validation 存在时注册）。
 *
 * <p><b>职责分层：</b>
 *
 * <ul>
 *   <li>本类：处理框架级、业务级、系统级异常（最高优先级）
 *   <li>{@link ValidationExceptionHandler}：处理参数校验异常
 * </ul>
 *
 * <p><b>指标记录：</b>所有 handler 方法统一调用 {@link #recordMetrics(Throwable)} 记录异常指标， 确保所有异常类型都被纳入监控。
 *
 * <p><b>HTTP 状态码：</b>使用 {@link HttpServletResponse#setStatus(int)} 动态设置 与异常对象中声明的 HTTP 状态码一致的响应状态码。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see BaseExceptionHandler
 * @see ValidationExceptionHandler
 * @see YdszExceptionHandlerAutoConfiguration
 */
@Slf4j
  // CHECKSTYLE.OFF: RegexpSinglelineJava — 字符串常量（注解/反射类名），非代码引用
@ConditionalOnClass(name = "org.springframework.web.bind.annotation.RestControllerAdvice")
  // CHECKSTYLE.ON: RegexpSinglelineJava
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class MvcExceptionHandler extends BaseExceptionHandler {

  /** 非法参数兜底文案（i18n key 解析失败时使用，避免泄露内部异常细节） */
  private static final String DEFAULT_ILLEGAL_ARGUMENT_MESSAGE = "非法参数";

  private final MessageSource messageSource;

  /**
   * 构造 MVC 全局异常处理器。
   *
   * @param environment Spring 环境对象
   * @param messageSource 国际化消息源
   * @param exceptionMetrics 异常指标统计器
   * @param properties 异常模块配置属性（可为 null）
   * @param eventPublisherProvider 事件发布器提供者
   */
  public MvcExceptionHandler(
      Environment environment,
      MessageSource messageSource,
      ExceptionMetrics exceptionMetrics,
      ExceptionProperties properties,
      ObjectProvider<ApplicationEventPublisher> eventPublisherProvider) {
    super(environment);
    this.messageSource = messageSource;
    setMessageSource(messageSource);
    setExceptionMetrics(environment, exceptionMetrics);
    setExceptionProperties(environment, properties);
    setEventPublisher(eventPublisherProvider.getIfAvailable());
  }

  @Override
  protected String getLogPrefix() {
    return "【全局】";
  }

  /**
   * 设置 HTTP 响应状态码（与异常对象中的 httpStatus 一致）
   *
   * @param response HTTP 响应
   * @param httpStatus HTTP 状态码
   */
  private void setResponseStatus(HttpServletResponse response, int httpStatus) {
    if (response != null) {
      response.setStatus(httpStatus);
    }
  }

  // ============================ 异常处理方法 ============================

  /**
   * 处理批量操作异常（HTTP 207 Multi-Status）
   *
   * <p>批量操作中部分成功部分失败时，返回 207 状态码 + 成功/失败明细。 此处理器必须在 {@link #handleBusinessException} 之前声明， 因为
   * BatchBusinessException 继承自 BusinessException。
   *
   * @param e 异常对象
   * @param request HTTP 请求
   * @param response HTTP 响应
   * @return 处理结果
   */
  @ExceptionHandler(BatchBusinessException.class)
  public Object handleBatchBusinessException(
      BatchBusinessException e, HttpServletRequest request, HttpServletResponse response) {
    recordMetrics(e);
    if (e.isAllSuccess()) {
      // 全部成功时降级为 200
      response.setStatus(HttpStatus.OK.value());
    } else {
      response.setStatus(207); // HTTP Multi-Status
    }

    String traceId = extractTraceId(request);
    String resolvedMsg = e.getMessage();
    publishExceptionEvent(e, request.getRequestURI(), traceId, resolvedMsg);

    // 构建 207 响应体
    Map<String, Object> batchResult = new LinkedHashMap<>(16);
    batchResult.put("successCount", e.getSuccessCount());
    batchResult.put("failureCount", e.getFailureCount());
    batchResult.put("totalCount", e.getTotalCount());
    batchResult.put("successItems", e.getSuccessItems());
    batchResult.put("failureItems", e.getFailureItems());
    batchResult.put("aggregation", e.getFailureAggregation());

    log.warn(
        "{}批量操作部分成功 | 路径: {} | 成功: {} | 失败: {} | traceId: {}",
        getLogPrefix(),
        request.getRequestURI(),
        e.getSuccessCount(),
        e.getFailureCount(),
        traceId);

    return YdszResponse.builder()
        .code(e.getCode())
        .msg(resolvedMsg)
        .data(batchResult)
        .traceId(traceId)
        .build();
  }

  /**
   * 处理业务异常（动态 HTTP 状态码）
   *
   * @param e 异常对象
   * @param request HTTP 请求
   * @param response HTTP 响应
   * @return 处理结果
   */
  @ExceptionHandler(BusinessException.class)
  public Object handleBusinessException(
      BusinessException e, HttpServletRequest request, HttpServletResponse response) {
    recordMetrics(e);
    log.warn(
        "{}业务异常 | 路径: {} | 错误码: {} | 消息: {}",
        getLogPrefix(),
        request.getRequestURI(),
        e.getCode(),
        e.getMessage(),
        e);

    setResponseStatus(response, e.getHttpStatus());
    addRetryAfterHeader(response, e);
    String traceId = extractTraceId(request);
    String resolvedMsg = e.getMessage();
    publishExceptionEvent(e, request.getRequestURI(), traceId, resolvedMsg);
    return buildResponse(e, request.getRequestURI(), traceId);
  }

  /**
   * 处理系统异常
   *
   * @param e 异常对象
   * @param request HTTP 请求
   * @param response HTTP 响应
   * @return 处理结果
   */
  @ExceptionHandler(SysException.class)
  public Object handleSysException(
      SysException e, HttpServletRequest request, HttpServletResponse response) {
    recordMetrics(e);
    log.error(
        "{}系统异常 | 路径: {} | 错误码: {} | 消息: {}",
        getLogPrefix(),
        request.getRequestURI(),
        e.getCode(),
        e.getMessage(),
        e);

    setResponseStatus(response, e.getHttpStatus());
    addRetryAfterHeader(response, e);
    String traceId = extractTraceId(request);
    String resolvedMsg = e.getMessage();
    publishExceptionEvent(e, request.getRequestURI(), traceId, resolvedMsg);
    return buildResponse(e, request.getRequestURI(), traceId);
  }

  /**
   * 处理其他 YDSZ 异常（兜底，捕获所有 AbstractYdszException 子类）
   *
   * @param e 异常对象
   * @param request HTTP 请求
   * @param response HTTP 响应
   * @return 处理结果
   */
  @ExceptionHandler(AbstractYdszException.class)
  public Object handleAbstractYdszException(
      AbstractYdszException e, HttpServletRequest request, HttpServletResponse response) {
    recordMetrics(e);
    log.warn(
        "{}异常 | 路径: {} | 错误码: {} | 消息: {} | 类型: {}",
        getLogPrefix(),
        request.getRequestURI(),
        e.getCode(),
        e.getMessage(),
        e.getClass().getSimpleName(),
        e);

    setResponseStatus(response, e.getHttpStatus());
    addRetryAfterHeader(response, e);
    String traceId = extractTraceId(request);
    String resolvedMsg = e.getMessage();
    publishExceptionEvent(e, request.getRequestURI(), traceId, resolvedMsg);
    return buildResponse(e, request.getRequestURI(), traceId);
  }

  /**
   * 处理请求体解析异常
   *
   * @param e 异常对象
   * @param request HTTP 请求
   * @return 处理结果
   */
  @ExceptionHandler(HttpMessageNotReadableException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public Object handleHttpMessageNotReadableException(
      HttpMessageNotReadableException e, HttpServletRequest request) {
    recordMetrics(e);
    log.error(
        "{}请求体解析异常 | 路径: {} | 消息: {}", getLogPrefix(), request.getRequestURI(), e.getMessage(), e);

    String message = resolveMessage("invalid.request.format", null, "请求格式错误");
    return buildStandardErrorResponse(
        CoreExceptionCode.INVALID_REQUEST_FORMAT.getCode(),
        CoreExceptionCode.INVALID_REQUEST_FORMAT.getKey(),
        message,
        HttpStatus.BAD_REQUEST.value(),
        request.getRequestURI());
  }

  /**
   * 处理缺少请求参数异常
   *
   * @param e 异常对象
   * @param request HTTP 请求
   * @return 处理结果
   */
  @ExceptionHandler(MissingServletRequestParameterException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public YdszResponse<?> handleMissingServletRequestParameterException(
      MissingServletRequestParameterException e, HttpServletRequest request) {
    recordMetrics(e);
    String message =
        resolveMessage("missing.request.parameter", new Object[] {e.getParameterName()}, "缺少请求参数");
    return buildValidationErrorResponse(
        CoreExceptionCode.ILLEGAL_ARGUMENT,
        message,
        HttpStatus.BAD_REQUEST.value(),
        request.getRequestURI(),
        e);
  }

  /**
   * 处理请求参数类型不匹配异常
   *
   * @param e 异常对象
   * @param request HTTP 请求
   * @return 处理结果
   */
  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public YdszResponse<?> handleMethodArgumentTypeMismatchException(
      MethodArgumentTypeMismatchException e, HttpServletRequest request) {
    recordMetrics(e);
    Class<?> requiredType = e.getRequiredType();
    String message =
        resolveMessage(
            "type.mismatch",
            new Object[] {e.getName(), requiredType != null ? requiredType.getSimpleName() : "未知"},
            "参数类型不匹配");
    return buildValidationErrorResponse(
        CoreExceptionCode.ILLEGAL_ARGUMENT,
        message,
        HttpStatus.BAD_REQUEST.value(),
        request.getRequestURI(),
        e);
  }

  /**
   * 处理缺少请求头异常
   *
   * @param e 异常对象
   * @param request HTTP 请求
   * @return 处理结果
   */
  @ExceptionHandler(MissingRequestHeaderException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public YdszResponse<?> handleMissingRequestHeaderException(
      MissingRequestHeaderException e, HttpServletRequest request) {
    recordMetrics(e);
    String message =
        resolveMessage("missing.request.header", new Object[] {e.getHeaderName()}, "缺少请求头");
    return buildValidationErrorResponse(
        CoreExceptionCode.ILLEGAL_ARGUMENT,
        message,
        HttpStatus.BAD_REQUEST.value(),
        request.getRequestURI(),
        e);
  }

  /**
   * 处理请求方法不支持异常
   *
   * @param e 异常对象
   * @param request HTTP 请求
   * @return 处理结果
   */
  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
  public YdszResponse<?> handleHttpRequestMethodNotSupportedException(
      HttpRequestMethodNotSupportedException e, HttpServletRequest request) {
    recordMetrics(e);
    String message =
        resolveMessage("method.not.supported", new Object[] {e.getMethod()}, "不支持的请求方法");
    return buildValidationErrorResponse(
        CoreExceptionCode.ILLEGAL_ARGUMENT,
        message,
        HttpStatus.METHOD_NOT_ALLOWED.value(),
        request.getRequestURI(),
        e);
  }

  /**
   * 处理文件上传大小超限异常
   *
   * @param e 异常对象
   * @param request HTTP 请求
   * @return 处理结果
   */
  @ExceptionHandler(MaxUploadSizeExceededException.class)
  @ResponseStatus(HttpStatus.CONTENT_TOO_LARGE)
  public Object handleMaxUploadSizeExceededException(
      MaxUploadSizeExceededException e, HttpServletRequest request) {
    recordMetrics(e);
    String message = resolveMessage("file.size.exceeded.message", null, "上传文件大小超出限制");
    log.error("{}文件上传超限 | 路径: {} | 消息: {}", getLogPrefix(), request.getRequestURI(), message, e);
    return buildStandardErrorResponse(
        CoreExceptionCode.FILE_SIZE_EXCEEDED.getCode(),
        CoreExceptionCode.FILE_SIZE_EXCEEDED.getKey(),
        message,
        HttpStatus.CONTENT_TOO_LARGE.value(),
        request.getRequestURI());
  }

  /**
   * 处理 404 异常
   *
   * @param e 异常对象
   * @param request HTTP 请求
   * @return 处理结果
   */
  @ExceptionHandler(NoHandlerFoundException.class)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public Object handleNoHandlerFoundException(
      NoHandlerFoundException e, HttpServletRequest request) {
    recordMetrics(e);
    String message =
        resolveMessage(
            "resource.not.found.detail", new Object[] {request.getRequestURI()}, "资源不存在");
    log.error("{}资源不存在 | 路径: {} | 消息: {}", getLogPrefix(), request.getRequestURI(), message, e);
    return buildStandardErrorResponse(
        CoreExceptionCode.RESOURCE_NOT_FOUND.getCode(),
        CoreExceptionCode.RESOURCE_NOT_FOUND.getKey(),
        message,
        HttpStatus.NOT_FOUND.value(),
        request.getRequestURI());
  }

  /**
   * 处理非法参数异常
   *
   * @param e 异常对象
   * @param request HTTP 请求
   * @return 处理结果
   */
  @ExceptionHandler(IllegalArgumentException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public Object handleIllegalArgumentException(
      IllegalArgumentException e, HttpServletRequest request) {
    recordMetrics(e);
    log.error(
        "{}非法参数异常 | 路径: {} | 消息: {}", getLogPrefix(), request.getRequestURI(), e.getMessage(), e);

    // 按请求 Locale 解析 i18n 文案，原始异常消息仅保留在日志中，避免泄露内部细节
    String message =
        resolveMessage(
            CoreExceptionCode.ILLEGAL_ARGUMENT.getKey(), null, DEFAULT_ILLEGAL_ARGUMENT_MESSAGE);
    return buildStandardErrorResponse(
        CoreExceptionCode.ILLEGAL_ARGUMENT.getCode(),
        CoreExceptionCode.ILLEGAL_ARGUMENT.getKey(),
        message,
        HttpStatus.BAD_REQUEST.value(),
        request.getRequestURI());
  }

  /**
   * 处理非法状态异常
   *
   * <p>IllegalStateException 属于系统级异常（非业务异常），统一返回 SYSTEM_ERROR， 避免暴露内部状态信息。业务层的"状态无效"应使用 {@link
   * BusinessException}。
   *
   * @param e 异常对象
   * @param request HTTP 请求
   * @return 处理结果
   */
  @ExceptionHandler(IllegalStateException.class)
  @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
  public Object handleIllegalStateException(IllegalStateException e, HttpServletRequest request) {
    recordMetrics(e);
    log.error(
        "{}非法状态异常 | 路径: {} | 消息: {}", getLogPrefix(), request.getRequestURI(), e.getMessage(), e);

    String message = resolveMessage("system.error", null, "系统异常，请联系管理员");

    ExceptionInfo info = buildExceptionInfo(e, request.getRequestURI(), extractTraceId(request));
    info.setCode(CoreExceptionCode.SYSTEM_ERROR.getCode());
    info.setMessage(message);

    return errorResponse(
        CoreExceptionCode.SYSTEM_ERROR.getCode(), message, includeExceptionInfo() ? info : null);
  }

  /**
   * 处理空指针异常
   *
   * @param e 异常对象
   * @param request HTTP 请求
   * @return 处理结果
   */
  @ExceptionHandler(NullPointerException.class)
  @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
  public Object handleNullPointerException(NullPointerException e, HttpServletRequest request) {
    recordMetrics(e);
    log.error(
        "{}空指针异常 | 路径: {} | 消息: {}", getLogPrefix(), request.getRequestURI(), e.getMessage(), e);

    String message = resolveMessage("system.error", null, "系统异常，请联系管理员");

    ExceptionInfo info = buildExceptionInfo(e, request.getRequestURI(), extractTraceId(request));
    info.setCode(CoreExceptionCode.SYSTEM_ERROR.getCode());
    info.setMessage(message);

    return errorResponse(
        CoreExceptionCode.SYSTEM_ERROR.getCode(), message, includeExceptionInfo() ? info : null);
  }

  /**
   * 处理所有未捕获的异常
   *
   * @param e 异常对象
   * @param request HTTP 请求
   * @return 处理结果
   */
  @ExceptionHandler(Exception.class)
  @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
  public Object handleException(Exception e, HttpServletRequest request) {
    recordMetrics(e);
    log.error(
        "{}系统异常 | 路径: {} | 类型: {} | 消息: {}",
        getLogPrefix(),
        request.getRequestURI(),
        e.getClass().getName(),
        e.getMessage(),
        e);

    String traceId = extractTraceId(request);
    String message = resolveMessage("system.error", null, "系统异常，请联系管理员");
    publishExceptionEvent(e, request.getRequestURI(), traceId, message);

    ExceptionInfo info = buildExceptionInfo(e, request.getRequestURI(), traceId);

    return errorResponse(
        CoreExceptionCode.SYSTEM_ERROR.getCode(), message, includeExceptionInfo() ? info : null);
  }
}
