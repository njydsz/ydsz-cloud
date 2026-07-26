package com.njydsz.common.exception.handler;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
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

import com.njydsz.common.core.constant.HeaderConstants;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.exception.alert.ExceptionAlertPublisher;
import com.njydsz.common.exception.code.UnifiedExceptionCode;
import com.njydsz.common.exception.config.ExceptionProperties;
import com.njydsz.common.exception.core.ExceptionInfo;
import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.exception.custom.ConcurrencyException;
import com.njydsz.common.exception.custom.DuplicateException;
import com.njydsz.common.exception.custom.ExternalException;
import com.njydsz.common.exception.custom.InfrastructureException;
import com.njydsz.common.exception.custom.RateLimitException;
import com.njydsz.common.exception.custom.SysException;
import com.njydsz.common.exception.custom.ValidationException;
import com.njydsz.common.exception.custom.YdszSecurityException;
import com.njydsz.common.exception.custom.YdszTimeoutException;
import com.njydsz.common.exception.metrics.ExceptionMetrics;
import com.njydsz.common.exception.observability.TraceContext;

import lombok.extern.slf4j.Slf4j;

/**
 * Spring MVC 全局异常处理器（非 Validation 部分）
 *
 * <p>处理业务异常、系统异常、安全异常等通用异常。
 * Validation 相关异常处理由 {@link ValidationExceptionHandler} 负责（仅在 jakarta.validation 存在时注册）。
 *
 * <p><b>职责分层：</b>
 * <ul>
 *   <li>本类：处理框架级、业务级、安全级异常（最高优先级）</li>
 *   <li>{@link ValidationExceptionHandler}：处理参数校验异常</li>
 * </ul>
 *
 * <p><b>指标记录：</b>所有 handler 方法统一调用 {@link #recordMetrics(Throwable)} 记录异常指标，
 * 确保所有异常类型都被纳入监控。
 *
 * <p><b>HTTP 状态码：</b>使用 {@link HttpServletResponse#setStatus(int)} 动态设置
 * 与异常对象中声明的 HTTP 状态码一致的响应状态码。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see BaseExceptionHandler
 * @see ValidationExceptionHandler
 * @see MvcExceptionHandlerAutoConfiguration
 */
@Slf4j
@ConditionalOnClass(name = "org.springframework.web.bind.annotation.RestControllerAdvice")
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class MvcExceptionHandler extends BaseExceptionHandler {

    private final MessageSource messageSource;

    /**
     * 构造 MVC 全局异常处理器
     *
     * @param messageSource   国际化消息源
     * @param exceptionMetrics 异常指标统计器
     * @param properties      异常模块配置属性
     * @param alertPublisher  异常告警发布器
     */
    public MvcExceptionHandler(MessageSource messageSource,
                               ExceptionMetrics exceptionMetrics,
                               ExceptionProperties properties,
                               ExceptionAlertPublisher alertPublisher) {
        this.messageSource = messageSource;
        setExceptionMetrics(exceptionMetrics);
        setExceptionProperties(properties);
        setAlertPublisher(alertPublisher);
    }

    @Override
    protected String getLogPrefix() {
        return "【全局】";
    }

    /**
     * 从 HttpServletRequest / MDC 提取 traceId
     *
     * <p>优先级：MDC > Request Header
     */
    private String extractTraceId(HttpServletRequest request) {
        String traceId = TraceContext.getTraceId();
        if (traceId == null && request != null) {
            traceId = request.getHeader(TraceContext.HEADER_TRACE_ID);
            if (traceId == null) {
                traceId = request.getHeader(HeaderConstants.X_REQUEST_ID);
            }
        }
        return traceId;
    }

    /**
     * 设置 HTTP 响应状态码（与异常对象中的 httpStatus 一致）
     */
    private void setResponseStatus(HttpServletResponse response, int httpStatus) {
        if (response != null) {
            response.setStatus(httpStatus);
        }
    }

    // ============================ 异常处理方法 ============================

    /**
     * 处理业务异常（动态 HTTP 状态码）
     */
    @ExceptionHandler(BusinessException.class)
    public Object handleBusinessException(BusinessException e, HttpServletRequest request,
                                           HttpServletResponse response) {
        recordMetrics(e);
        log.warn("{}业务异常 | 路径: {} | 错误码: {} | 消息: {}",
                getLogPrefix(), request.getRequestURI(), e.getCode(), e.getMessage(), e);

        setResponseStatus(response, e.getHttpStatus());
        String traceId = extractTraceId(request);
        if (useProblemDetail()) {
            return buildResponseEntity(buildProblemDetail(e, request.getRequestURI(), traceId), e);
        }
        ExceptionInfo info = buildExceptionInfo(e, request.getRequestURI(), traceId);
        return BaseResponse.error(e.getCode(), e.getMessage(), info);
    }

    /**
     * 处理并发冲突异常
     */
    @ExceptionHandler(ConcurrencyException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Object handleConcurrencyException(ConcurrencyException e, HttpServletRequest request) {
        recordMetrics(e);
        log.warn("{}并发冲突异常 | 路径: {} | 错误码: {} | 消息: {}",
                getLogPrefix(), request.getRequestURI(), e.getCode(), e.getMessage(), e);

        return buildResponse(e, request.getRequestURI(), extractTraceId(request));
    }

    /**
     * 处理重复提交异常
     */
    @ExceptionHandler(DuplicateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Object handleDuplicateException(DuplicateException e, HttpServletRequest request) {
        recordMetrics(e);
        log.warn("{}重复提交异常 | 路径: {} | 错误码: {} | 消息: {}",
                getLogPrefix(), request.getRequestURI(), e.getCode(), e.getMessage(), e);

        return buildResponse(e, request.getRequestURI(), extractTraceId(request));
    }

    /**
     * 处理限流异常
     */
    @ExceptionHandler(RateLimitException.class)
    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
    public Object handleRateLimitException(RateLimitException e, HttpServletRequest request) {
        recordMetrics(e);
        log.warn("{}限流异常 | 路径: {} | 错误码: {} | 消息: {}",
                getLogPrefix(), request.getRequestURI(), e.getCode(), e.getMessage(), e);

        return buildResponse(e, request.getRequestURI(), extractTraceId(request));
    }

    /**
     * 处理请求体解析异常
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Object handleHttpMessageNotReadableException(
            HttpMessageNotReadableException e, HttpServletRequest request) {
        recordMetrics(e);
        log.error("{}请求体解析异常 | 路径: {} | 消息: {}", getLogPrefix(), request.getRequestURI(), e.getMessage(), e);

        String message = messageSource.getMessage("invalid.request.format", null,
                "请求格式错误", LocaleContextHolder.getLocale());

        ExceptionInfo info = new ExceptionInfo(
                UnifiedExceptionCode.INVALID_REQUEST_FORMAT.getCode(),
                UnifiedExceptionCode.INVALID_REQUEST_FORMAT.getKey(),
                message,
                HttpStatus.BAD_REQUEST.value()
        );
        info.setPath(request.getRequestURI());
        return BaseResponse.error(
                UnifiedExceptionCode.INVALID_REQUEST_FORMAT.getCode(),
                message,
                includeExceptionInfo() ? info : null
        );
    }

    /**
     * 处理缺少请求参数异常
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public BaseResponse<?> handleMissingServletRequestParameterException(
            MissingServletRequestParameterException e, HttpServletRequest request) {
        recordMetrics(e);
        String message = messageSource.getMessage("missing.request.parameter",
                new Object[]{e.getParameterName()}, "缺少请求参数", LocaleContextHolder.getLocale());
        return buildValidationErrorResponse(
                UnifiedExceptionCode.ILLEGAL_ARGUMENT, message, HttpStatus.BAD_REQUEST.value(),
                request.getRequestURI(), e);
    }

    /**
     * 处理请求参数类型不匹配异常
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public BaseResponse<?> handleMethodArgumentTypeMismatchException(
            MethodArgumentTypeMismatchException e, HttpServletRequest request) {
        recordMetrics(e);
        Class<?> requiredType = e.getRequiredType();
        String message = messageSource.getMessage("type.mismatch",
                new Object[]{e.getName(), requiredType != null ? requiredType.getSimpleName() : "未知"},
                "参数类型不匹配", LocaleContextHolder.getLocale());
        return buildValidationErrorResponse(
                UnifiedExceptionCode.ILLEGAL_ARGUMENT, message, HttpStatus.BAD_REQUEST.value(),
                request.getRequestURI(), e);
    }

    /**
     * 处理缺少请求头异常
     */
    @ExceptionHandler(MissingRequestHeaderException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public BaseResponse<?> handleMissingRequestHeaderException(
            MissingRequestHeaderException e, HttpServletRequest request) {
        recordMetrics(e);
        String message = messageSource.getMessage("missing.request.header",
                new Object[]{e.getHeaderName()}, "缺少请求头", LocaleContextHolder.getLocale());
        return buildValidationErrorResponse(
                UnifiedExceptionCode.ILLEGAL_ARGUMENT, message, HttpStatus.BAD_REQUEST.value(),
                request.getRequestURI(), e);
    }

    /**
     * 处理请求方法不支持异常
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public BaseResponse<?> handleHttpRequestMethodNotSupportedException(
            HttpRequestMethodNotSupportedException e, HttpServletRequest request) {
        recordMetrics(e);
        String message = messageSource.getMessage("method.not.supported",
                new Object[]{e.getMethod()}, "不支持的请求方法", LocaleContextHolder.getLocale());
        return buildValidationErrorResponse(
                UnifiedExceptionCode.ILLEGAL_ARGUMENT, message, HttpStatus.METHOD_NOT_ALLOWED.value(),
                request.getRequestURI(), e);
    }

    /**
     * 处理文件上传大小超限异常
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.CONTENT_TOO_LARGE)
    public Object handleMaxUploadSizeExceededException(
            MaxUploadSizeExceededException e, HttpServletRequest request) {
        recordMetrics(e);
        String message = messageSource.getMessage("file.size.exceeded.message", null,
                "上传文件大小超出限制", LocaleContextHolder.getLocale());
        log.error("{}文件上传超限 | 路径: {} | 消息: {}", getLogPrefix(), request.getRequestURI(), message, e);

        ExceptionInfo info = new ExceptionInfo(
                UnifiedExceptionCode.FILE_SIZE_EXCEEDED.getCode(),
                UnifiedExceptionCode.FILE_SIZE_EXCEEDED.getKey(),
                message,
                HttpStatus.CONTENT_TOO_LARGE.value()
        );
        info.setPath(request.getRequestURI());
        return BaseResponse.error(
                UnifiedExceptionCode.FILE_SIZE_EXCEEDED.getCode(),
                message,
                includeExceptionInfo() ? info : null
        );
    }

    /**
     * 处理 404 异常
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Object handleNoHandlerFoundException(
            NoHandlerFoundException e, HttpServletRequest request) {
        recordMetrics(e);
        String message = messageSource.getMessage("resource.not.found.detail",
                new Object[]{request.getRequestURI()}, "资源不存在", LocaleContextHolder.getLocale());
        log.error("{}资源不存在 | 路径: {} | 消息: {}", getLogPrefix(), request.getRequestURI(), message, e);

        ExceptionInfo info = new ExceptionInfo(
                UnifiedExceptionCode.RESOURCE_NOT_FOUND.getCode(),
                UnifiedExceptionCode.RESOURCE_NOT_FOUND.getKey(),
                message,
                HttpStatus.NOT_FOUND.value()
        );
        info.setPath(request.getRequestURI());
        return BaseResponse.error(
                UnifiedExceptionCode.RESOURCE_NOT_FOUND.getCode(),
                message,
                includeExceptionInfo() ? info : null
        );
    }

    /**
     * 处理非法参数异常
     */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Object handleIllegalArgumentException(
            IllegalArgumentException e, HttpServletRequest request) {
        recordMetrics(e);
        log.error("{}非法参数异常 | 路径: {} | 消息: {}", getLogPrefix(), request.getRequestURI(), e.getMessage(), e);

        ExceptionInfo info = new ExceptionInfo(
                UnifiedExceptionCode.ILLEGAL_ARGUMENT.getCode(),
                UnifiedExceptionCode.ILLEGAL_ARGUMENT.getKey(),
                e.getMessage(),
                HttpStatus.BAD_REQUEST.value()
        );
        info.setPath(request.getRequestURI());
        return BaseResponse.error(
                UnifiedExceptionCode.ILLEGAL_ARGUMENT.getCode(),
                e.getMessage(),
                includeExceptionInfo() ? info : null
        );
    }

    /**
     * 处理系统异常
     */
    @ExceptionHandler(SysException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Object handleSysException(SysException e, HttpServletRequest request) {
        recordMetrics(e);
        log.error("{}系统异常 | 路径: {} | 错误码: {} | 消息: {}",
                getLogPrefix(), request.getRequestURI(), e.getCode(), e.getMessage(), e);

        return buildResponse(e, request.getRequestURI(), extractTraceId(request));
    }

    /**
     * 处理安全异常
     */
    @ExceptionHandler(YdszSecurityException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Object handleSecurityException(YdszSecurityException e, HttpServletRequest request) {
        recordMetrics(e);
        log.warn("{}安全异常 | 路径: {} | 错误码: {} | 消息: {}",
                getLogPrefix(), request.getRequestURI(), e.getCode(), e.getMessage(), e);

        return buildResponse(e, request.getRequestURI(), extractTraceId(request));
    }

    /**
     * 处理校验异常
     */
    @ExceptionHandler(ValidationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Object handleValidationException(ValidationException e, HttpServletRequest request) {
        recordMetrics(e);
        log.warn("{}校验异常 | 路径: {} | 错误码: {} | 消息: {}",
                getLogPrefix(), request.getRequestURI(), e.getCode(), e.getMessage(), e);

        return buildResponse(e, request.getRequestURI(), extractTraceId(request));
    }

    /**
     * 处理超时异常
     */
    @ExceptionHandler(YdszTimeoutException.class)
    @ResponseStatus(HttpStatus.GATEWAY_TIMEOUT)
    public Object handleTimeoutException(YdszTimeoutException e, HttpServletRequest request) {
        recordMetrics(e);
        log.error("{}超时异常 | 路径: {} | 错误码: {} | 消息: {}",
                getLogPrefix(), request.getRequestURI(), e.getCode(), e.getMessage(), e);

        return buildResponse(e, request.getRequestURI(), extractTraceId(request));
    }

    /**
     * 处理外部服务异常
     */
    @ExceptionHandler(ExternalException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public Object handleExternalException(ExternalException e, HttpServletRequest request) {
        recordMetrics(e);
        log.error("{}外部服务异常 | 路径: {} | 错误码: {} | 消息: {}",
                getLogPrefix(), request.getRequestURI(), e.getCode(), e.getMessage(), e);

        return buildResponse(e, request.getRequestURI(), extractTraceId(request));
    }

    /**
     * 处理基础设施异常
     */
    @ExceptionHandler(InfrastructureException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Object handleInfrastructureException(InfrastructureException e, HttpServletRequest request) {
        recordMetrics(e);
        log.error("{}基础设施异常 | 路径: {} | 错误码: {} | 消息: {}",
                getLogPrefix(), request.getRequestURI(), e.getCode(), e.getMessage(), e);

        return buildResponse(e, request.getRequestURI(), extractTraceId(request));
    }

    /**
     * 处理非法状态异常
     *
     * <p>IllegalStateException 属于系统级异常（非业务异常），统一返回 SYSTEM_ERROR，
     * 避免暴露内部状态信息。业务层的"状态无效"应使用 {@link BusinessException}。
     */
    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Object handleIllegalStateException(
            IllegalStateException e, HttpServletRequest request) {
        recordMetrics(e);
        log.error("{}非法状态异常 | 路径: {} | 消息: {}", getLogPrefix(), request.getRequestURI(), e.getMessage(), e);

        String message = messageSource.getMessage("system.error", null,
                "系统异常，请联系管理员", LocaleContextHolder.getLocale());

        ExceptionInfo info = buildExceptionInfo(e, request.getRequestURI(), extractTraceId(request));
        info.setCode(UnifiedExceptionCode.SYSTEM_ERROR.getCode());
        info.setMessage(message);

        return BaseResponse.error(
                UnifiedExceptionCode.SYSTEM_ERROR.getCode(),
                message,
                includeExceptionInfo() ? info : null
        );
    }

    /**
     * 处理空指针异常
     */
    @ExceptionHandler(NullPointerException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Object handleNullPointerException(
            NullPointerException e, HttpServletRequest request) {
        recordMetrics(e);
        log.error("{}空指针异常 | 路径: {} | 消息: {}", getLogPrefix(), request.getRequestURI(), e.getMessage(), e);

        String message = messageSource.getMessage("system.error", null,
                "系统异常，请联系管理员", LocaleContextHolder.getLocale());

        ExceptionInfo info = buildExceptionInfo(e, request.getRequestURI(), extractTraceId(request));
        info.setCode(UnifiedExceptionCode.SYSTEM_ERROR.getCode());
        info.setMessage(message);

        return BaseResponse.error(
                UnifiedExceptionCode.SYSTEM_ERROR.getCode(),
                message,
                includeExceptionInfo() ? info : null
        );
    }

    /**
     * 处理所有未捕获的异常
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Object handleException(Exception e, HttpServletRequest request) {
        recordMetrics(e);
        log.error("{}系统异常 | 路径: {} | 类型: {} | 消息: {}",
                getLogPrefix(), request.getRequestURI(), e.getClass().getName(), e.getMessage(), e);

        String message = messageSource.getMessage("system.error", null,
                "系统异常，请联系管理员", LocaleContextHolder.getLocale());

        ExceptionInfo info = buildExceptionInfo(e, request.getRequestURI(), extractTraceId(request));

        return BaseResponse.error(
                UnifiedExceptionCode.SYSTEM_ERROR.getCode(),
                message,
                includeExceptionInfo() ? info : null
        );
    }
}
