package com.njydsz.common.exception.handler;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
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

import com.njydsz.common.core.constant.HeaderConstants;
import com.njydsz.common.core.context.RequestContext;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.exception.batch.BatchBusinessException;
import com.njydsz.common.exception.code.CoreExceptionCode;
import com.njydsz.common.exception.config.ExceptionProperties;
import com.njydsz.common.exception.core.ExceptionInfo;
import com.njydsz.common.exception.custom.AbstractYdszException;
import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.exception.custom.SysException;
import com.njydsz.common.exception.metrics.ExceptionMetrics;

import lombok.extern.slf4j.Slf4j;

/**
 * Spring MVC 全局异常处理器（非 Validation 部分）
 *
 * <p>处理业务异常、系统异常等通用异常。
 * Validation 相关异常处理由 {@link ValidationExceptionHandler} 负责（仅在 jakarta.validation 存在时注册）。
 *
 * <p><b>职责分层：</b>
 * <ul>
 *   <li>本类：处理框架级、业务级、系统级异常（最高优先级）</li>
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
 * @see YdszExceptionHandlerAutoConfiguration
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
     * @param environment        Spring 环境对象
     * @param messageSource      国际化消息源
     * @param exceptionMetrics   异常指标统计器
     * @param properties         异常模块配置属性（可为 null）
     * @param eventPublisher     事件发布器（可为 null）
     */
    public MvcExceptionHandler(Environment environment,
                               MessageSource messageSource,
                               ExceptionMetrics exceptionMetrics,
                               ExceptionProperties properties,
                               ObjectProvider<ApplicationEventPublisher> eventPublisherProvider) {
        super(environment);
        this.messageSource = messageSource;
        setExceptionMetrics(environment, exceptionMetrics);
        setExceptionProperties(environment, properties);
        setEventPublisher(eventPublisherProvider.getIfAvailable());
    }

    @Override
    protected String getLogPrefix() {
        return "【全局】";
    }

    /**
     * 从 RequestContext / HttpServletRequest / MDC 提取 traceId
     *
     * <p>优先级：RequestContext > MDC > Request Header
     */
    private String extractTraceId(HttpServletRequest request) {
        String traceId = RequestContext.getTraceId();
        if (traceId == null || traceId.isBlank()) {
            traceId = MDC.get(HeaderConstants.MDC_TRACE_ID_KEY);
        }
        if ((traceId == null || traceId.isBlank()) && request != null) {
            traceId = request.getHeader(HeaderConstants.TRACE_ID_HEADER);
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
     * 处理批量操作异常（HTTP 207 Multi-Status）
     *
     * <p>批量操作中部分成功部分失败时，返回 207 状态码 + 成功/失败明细。
     * 此处理器必须在 {@link #handleBusinessException} 之前声明，
     * 因为 BatchBusinessException 继承自 BusinessException。
     */
    @ExceptionHandler(BatchBusinessException.class)
    public Object handleBatchBusinessException(BatchBusinessException e, HttpServletRequest request,
                                                HttpServletResponse response) {
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
        Map<String, Object> batchResult = new LinkedHashMap<>();
        batchResult.put("successCount", e.getSuccessCount());
        batchResult.put("failureCount", e.getFailureCount());
        batchResult.put("totalCount", e.getTotalCount());
        batchResult.put("successItems", e.getSuccessItems());
        batchResult.put("failureItems", e.getFailureItems());

        log.warn("{}批量操作部分成功 | 路径: {} | 成功: {} | 失败: {} | traceId: {}",
                getLogPrefix(), request.getRequestURI(), e.getSuccessCount(),
                e.getFailureCount(), traceId);

        return BaseResponse.builder()
                .code(e.getCode())
                .msg(resolvedMsg)
                .data(batchResult)
                .traceId(traceId)
                .build();
    }

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
        addRetryAfterHeader(response, e);
        String traceId = extractTraceId(request);
        String resolvedMsg = e.getMessage();
        publishExceptionEvent(e, request.getRequestURI(), traceId, resolvedMsg);
        return buildResponse(e, request.getRequestURI(), traceId);
    }

    /**
     * 处理系统异常
     */
    @ExceptionHandler(SysException.class)
    public Object handleSysException(SysException e, HttpServletRequest request,
                                      HttpServletResponse response) {
        recordMetrics(e);
        log.error("{}系统异常 | 路径: {} | 错误码: {} | 消息: {}",
                getLogPrefix(), request.getRequestURI(), e.getCode(), e.getMessage(), e);

        setResponseStatus(response, e.getHttpStatus());
        addRetryAfterHeader(response, e);
        String traceId = extractTraceId(request);
        String resolvedMsg = e.getMessage();
        publishExceptionEvent(e, request.getRequestURI(), traceId, resolvedMsg);
        return buildResponse(e, request.getRequestURI(), traceId);
    }

    /**
     * 处理其他 YDSZ 异常（兜底，捕获所有 AbstractYdszException 子类）
     */
    @ExceptionHandler(AbstractYdszException.class)
    public Object handleAbstractYdszException(AbstractYdszException e, HttpServletRequest request,
                                               HttpServletResponse response) {
        recordMetrics(e);
        log.warn("{}异常 | 路径: {} | 错误码: {} | 消息: {} | 类型: {}",
                getLogPrefix(), request.getRequestURI(), e.getCode(), e.getMessage(),
                e.getClass().getSimpleName(), e);

        setResponseStatus(response, e.getHttpStatus());
        addRetryAfterHeader(response, e);
        String traceId = extractTraceId(request);
        String resolvedMsg = e.getMessage();
        publishExceptionEvent(e, request.getRequestURI(), traceId, resolvedMsg);
        return buildResponse(e, request.getRequestURI(), traceId);
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
                CoreExceptionCode.INVALID_REQUEST_FORMAT.getCode(),
                CoreExceptionCode.INVALID_REQUEST_FORMAT.getKey(),
                message,
                HttpStatus.BAD_REQUEST.value()
        );
        info.setPath(request.getRequestURI());
        return errorResponse(
                CoreExceptionCode.INVALID_REQUEST_FORMAT.getCode(),
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
                CoreExceptionCode.ILLEGAL_ARGUMENT, message, HttpStatus.BAD_REQUEST.value(),
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
                CoreExceptionCode.ILLEGAL_ARGUMENT, message, HttpStatus.BAD_REQUEST.value(),
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
                CoreExceptionCode.ILLEGAL_ARGUMENT, message, HttpStatus.BAD_REQUEST.value(),
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
                CoreExceptionCode.ILLEGAL_ARGUMENT, message, HttpStatus.METHOD_NOT_ALLOWED.value(),
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
                CoreExceptionCode.FILE_SIZE_EXCEEDED.getCode(),
                CoreExceptionCode.FILE_SIZE_EXCEEDED.getKey(),
                message,
                HttpStatus.CONTENT_TOO_LARGE.value()
        );
        info.setPath(request.getRequestURI());
        return errorResponse(
                CoreExceptionCode.FILE_SIZE_EXCEEDED.getCode(),
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
                CoreExceptionCode.RESOURCE_NOT_FOUND.getCode(),
                CoreExceptionCode.RESOURCE_NOT_FOUND.getKey(),
                message,
                HttpStatus.NOT_FOUND.value()
        );
        info.setPath(request.getRequestURI());
        return errorResponse(
                CoreExceptionCode.RESOURCE_NOT_FOUND.getCode(),
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
                CoreExceptionCode.ILLEGAL_ARGUMENT.getCode(),
                CoreExceptionCode.ILLEGAL_ARGUMENT.getKey(),
                e.getMessage(),
                HttpStatus.BAD_REQUEST.value()
        );
        info.setPath(request.getRequestURI());
        return errorResponse(
                CoreExceptionCode.ILLEGAL_ARGUMENT.getCode(),
                e.getMessage(),
                includeExceptionInfo() ? info : null
        );
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
        info.setCode(CoreExceptionCode.SYSTEM_ERROR.getCode());
        info.setMessage(message);

        return errorResponse(
                CoreExceptionCode.SYSTEM_ERROR.getCode(),
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
        info.setCode(CoreExceptionCode.SYSTEM_ERROR.getCode());
        info.setMessage(message);

        return errorResponse(
                CoreExceptionCode.SYSTEM_ERROR.getCode(),
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

        String traceId = extractTraceId(request);
        String message = messageSource.getMessage("system.error", null,
                "系统异常，请联系管理员", LocaleContextHolder.getLocale());
        publishExceptionEvent(e, request.getRequestURI(), traceId, message);

        ExceptionInfo info = buildExceptionInfo(e, request.getRequestURI(), traceId);

        return errorResponse(
                CoreExceptionCode.SYSTEM_ERROR.getCode(),
                message,
                includeExceptionInfo() ? info : null
        );
    }
}
