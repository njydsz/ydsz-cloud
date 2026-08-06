package com.remisoft.common.exception.handler;

import java.util.List;
import java.util.stream.Collectors;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.MessageSource;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.validation.BindException;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.remisoft.common.core.constant.HeaderConstants;
import com.remisoft.common.core.context.RequestContext;
import com.remisoft.common.core.response.BaseResponse;
import com.remisoft.common.exception.code.UnifiedExceptionCode;
import com.remisoft.common.exception.config.ExceptionProperties;
import com.remisoft.common.exception.core.ExceptionInfo;
import com.remisoft.common.exception.metrics.ExceptionMetrics;

import org.slf4j.MDC;

/**
 * Validation 相关异常处理器
 *
 * <p>仅在 jakarta.validation 存在时注册，处理 {@code @Valid}/{@code @Validated} 注解触发的校验异常。
 * 与 {@link MvcExceptionHandler} 配合使用，后者处理通用异常，本类专注于校验异常。
 *
 * <p><b>重构说明：</b>继承 {@link BaseExceptionHandler} 复用公共逻辑（traceId 提取、
 * 响应构建、指标记录），消除约 40 行重复代码。
 *
 * <p><b>装配：</b>本类已不再直接标注 {@code @AutoConfiguration}，
 * 改由 {@link ValidationExceptionHandlerAutoConfiguration} 负责条件装配与 Bean 注入。
 *
 * @author remi-team
 * @since 1.0.0
 * @see MvcExceptionHandler
 * @see ValidationExceptionHandlerAutoConfiguration
 */
@ConditionalOnClass(ConstraintViolationException.class)
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@RestControllerAdvice
public class ValidationExceptionHandler extends BaseExceptionHandler {

    private final MessageSource messageSource;

    /**
     * 构造校验异常处理器
     *
     * @param environment     Spring 环境对象
     * @param messageSource   国际化消息源
     * @param exceptionMetrics 异常指标统计器（可选）
     * @param properties      异常模块配置属性（可选）
     */
    public ValidationExceptionHandler(Environment environment,
                                     MessageSource messageSource,
                                     ExceptionMetrics exceptionMetrics,
                                     ExceptionProperties properties) {
        super(environment);
        this.messageSource = messageSource;
        setExceptionMetrics(environment, exceptionMetrics);
        setExceptionProperties(environment, properties);
    }

    @Override
    protected String getLogPrefix() {
        return "【Validation】";
    }

    /**
     * 提取约束违反异常的错误消息
     */
    private String extractConstraintViolationMessages(ConstraintViolationException e) {
        List<String> messages = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.toList());
        return String.join(", ", messages);
    }

    /**
     * 提取绑定结果中的错误消息
     */
    private String extractBindingResultMessages(BindingResult bindingResult) {
        return bindingResult.getAllErrors().stream()
                .map(DefaultMessageSourceResolvable::getDefaultMessage)
                .collect(Collectors.joining(", "));
    }

    /**
     * 从 HttpServletRequest / MDC 提取 traceId
     *
     * <p>优先级：RequestContext > MDC > Request Header（X-Trace-Id > X-Request-Id）
     */
    private String extractTraceId(HttpServletRequest request) {
        String traceId = RequestContext.getTraceId();
        if (traceId == null || traceId.isBlank()) {
            traceId = MDC.get(HeaderConstants.MDC_TRACE_ID_KEY);
        }
        if ((traceId == null || traceId.isBlank()) && request != null) {
            traceId = request.getHeader(HeaderConstants.TRACE_ID_HEADER);
            if (traceId == null) {
                traceId = request.getHeader("X-Request-Id");
            }
        }
        return traceId;
    }

    /**
     * 处理参数校验异常（简单参数 @Validated）
     */
    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public BaseResponse<?> handleConstraintViolationException(ConstraintViolationException e,
                                                              HttpServletRequest request) {
        recordMetrics(e);
        log.warn("{}参数校验异常 | 路径: {} | 消息: {}", getLogPrefix(), request.getRequestURI(), e.getMessage());
        String message = extractConstraintViolationMessages(e);
        ExceptionInfo info = buildValidationInfo(message, request.getRequestURI(), extractTraceId(request));
        return errorResponse(UnifiedExceptionCode.ILLEGAL_ARGUMENT.getCode(), message,
                includeExceptionInfo() ? info : null);
    }

    /**
     * 处理请求体参数校验异常（@Valid/@Validated）
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public BaseResponse<?> handleMethodArgumentNotValidException(MethodArgumentNotValidException e,
                                                                 HttpServletRequest request) {
        recordMetrics(e);
        log.warn("{}请求体校验异常 | 路径: {} | 消息: {}", getLogPrefix(), request.getRequestURI(), e.getMessage());
        String message = extractBindingResultMessages(e.getBindingResult());
        ExceptionInfo info = buildValidationInfo(message, request.getRequestURI(), extractTraceId(request));
        return errorResponse(UnifiedExceptionCode.ILLEGAL_ARGUMENT.getCode(), message,
                includeExceptionInfo() ? info : null);
    }

    /**
     * 处理表单绑定异常
     */
    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public BaseResponse<?> handleBindException(BindException e, HttpServletRequest request) {
        recordMetrics(e);
        log.warn("{}表单绑定异常 | 路径: {} | 消息: {}", getLogPrefix(), request.getRequestURI(), e.getMessage());
        String message = extractBindingResultMessages(e.getBindingResult());
        ExceptionInfo info = buildValidationInfo(message, request.getRequestURI(), extractTraceId(request));
        return errorResponse(UnifiedExceptionCode.ILLEGAL_ARGUMENT.getCode(), message,
                includeExceptionInfo() ? info : null);
    }

    /**
     * 构建校验异常 ExceptionInfo
     */
    private ExceptionInfo buildValidationInfo(String message, String path, String traceId) {
        ExceptionInfo info = new ExceptionInfo();
        info.setCode(UnifiedExceptionCode.ILLEGAL_ARGUMENT.getCode());
        info.setKey(UnifiedExceptionCode.ILLEGAL_ARGUMENT.getKey());
        info.setMessage(message);
        info.setHttpStatus(HttpStatus.BAD_REQUEST.value());
        info.setPath(path);
        info.setTraceId(traceId);
        return info;
    }
}
