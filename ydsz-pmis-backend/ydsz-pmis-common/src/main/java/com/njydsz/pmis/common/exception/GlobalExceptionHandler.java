package com.njydsz.pmis.common.exception;

import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.common.util.TraceIdUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.util.stream.Collectors;

/**
 * 全局异常处理
 *
 * <p>统一处理 Controller 层异常，转换为 R 格式返回。
 *
 * <p>i18n 支持：通过 {@link MessageSource} 根据 Accept-Language 请求头解析本地化消息。
 * 当 {@code BizException} 仅由 {@code BizErrorCode} 构造（无自定义 message）时，
 * 使用 {@code error.{ENUM_NAME}} key 解析消息；当存在自定义 message 时直接使用该 message。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 国际化消息源（可选注入）。
     *
     * <p>使用字段注入而非构造器注入，以保留无参构造函数，
     * 便于单元测试中直接 {@code new GlobalExceptionHandler()} 实例化。
     * 当 messageSource 为 null（如单元测试）时，回退到 {@link BizErrorCode#getMessage()} 默认中文消息。
     */
    @Autowired
    private MessageSource messageSource;

    /**
     * 业务异常处理
     *
     * <p>当 {@code BizException} 仅用 {@code BizErrorCode} 构造（无自定义 message）时，
     * 通过 {@link MessageSource} 解析国际化消息；当存在自定义 message 时直接使用该 message，
     * 不经过 MessageSource。
     *
     * @param e   业务异常
     * @param req HTTP 请求
     * @return 统一响应
     */
    @ExceptionHandler(BizException.class)
    public Result<Void> handleBizException(BizException e, HttpServletRequest req) {
        log.warn("[BizException] {} {} - code={} message={}",
                req.getMethod(), req.getRequestURI(), e.getCode(), e.getMessage());
        String message = e.getErrorMessage();
        BizErrorCode errorCode = findErrorCode(e.getCode());
        if (errorCode != null && errorCode.getMessage().equals(message)) {
            // 仅当异常使用 BizErrorCode 默认 message 构造（无自定义 message）时，走 i18n 解析
            message = resolveMessage(errorCode);
        } else if (message != null && message.startsWith("error.")) {
            // 自定义 message 看起来是 i18n key（以 "error." 开头），尝试通过 MessageSource 解析
            message = resolveMessage(message, null, message);
        }
        Result<Void> r = Result.failed(e.getCode(), message);
        r.setTraceId(TraceIdUtil.get());
        return r;
    }

    /**
     * @Valid 校验失败 (RequestBody) 处理
     *
     * @param e 校验异常
     * @return 统一响应
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleValidException(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("[ValidationFailed] {}", msg);
        Result<Void> r = Result.failed(BizErrorCode.VALIDATION_FAILED.getCode(), msg);
        r.setTraceId(TraceIdUtil.get());
        return r;
    }

    /**
     * @Valid 校验失败 (Form) 处理
     *
     * @param e 绑定异常
     * @return 统一响应
     */
    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleBindException(BindException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("[BindException] {}", msg);
        Result<Void> r = Result.failed(BizErrorCode.VALIDATION_FAILED.getCode(), msg);
        r.setTraceId(TraceIdUtil.get());
        return r;
    }

    /**
     * @Validated 校验失败 (Path/Param) 处理
     *
     * @param e 约束违反异常
     * @return 统一响应
     */
    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleConstraintViolation(ConstraintViolationException e) {
        String msg = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining("; "));
        log.warn("[ConstraintViolation] {}", msg);
        Result<Void> r = Result.failed(BizErrorCode.VALIDATION_FAILED.getCode(), msg);
        r.setTraceId(TraceIdUtil.get());
        return r;
    }

    /**
     * 缺少必填参数处理
     *
     * <p>使用 {@code error.missing_parameter} 国际化消息模板，传入参数名占位符。
     *
     * @param e 缺少参数异常
     * @return 统一响应
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Result<Void> handleMissingParam(MissingServletRequestParameterException e) {
        String msg = resolveMessage("error.missing_parameter",
                new Object[]{e.getParameterName()},
                String.format("缺少必填参数: %s", e.getParameterName()));
        Result<Void> r = Result.failed(BizErrorCode.MISSING_PARAMETER.getCode(), msg);
        r.setTraceId(TraceIdUtil.get());
        return r;
    }

    /**
     * 请求体解析失败处理
     *
     * @param e 请求体不可读异常
     * @return 统一响应
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<Void> handleNotReadable(HttpMessageNotReadableException e) {
        log.warn("[HttpMessageNotReadable] {}", e.getMessage());
        Result<Void> r = Result.failed(BizErrorCode.BAD_REQUEST.getCode(), resolveMessage(BizErrorCode.BAD_REQUEST));
        r.setTraceId(TraceIdUtil.get());
        return r;
    }

    /**
     * 请求方法不允许处理
     *
     * @param e 请求方法不支持异常
     * @return 统一响应
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public Result<Void> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        Result<Void> r = Result.failed(BizErrorCode.METHOD_NOT_ALLOWED.getCode(), resolveMessage(BizErrorCode.METHOD_NOT_ALLOWED));
        r.setTraceId(TraceIdUtil.get());
        return r;
    }

    /**
     * 资源不存在处理
     *
     * @param e 找不到处理器异常
     * @return 统一响应
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public Result<Void> handleNotFound(NoHandlerFoundException e) {
        Result<Void> r = Result.failed(BizErrorCode.NOT_FOUND.getCode(), resolveMessage(BizErrorCode.NOT_FOUND));
        r.setTraceId(TraceIdUtil.get());
        return r;
    }

    /**
     * 非法参数处理
     *
     * @param e 非法参数异常
     * @return 统一响应
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public Result<Void> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("[IllegalArgument] {}", e.getMessage());
        Result<Void> r = Result.failed(BizErrorCode.BAD_REQUEST.getCode(), e.getMessage());
        r.setTraceId(TraceIdUtil.get());
        return r;
    }

    /**
     * 兜底异常处理（捕获所有未明确处理的异常）
     *
     * @param e   异常
     * @param req HTTP 请求
     * @return 统一响应
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleException(Exception e, HttpServletRequest req) {
        log.error("[SystemError] {} {}", req.getMethod(), req.getRequestURI(), e);
        String traceId = TraceIdUtil.get();
        String message = resolveMessage(BizErrorCode.INTERNAL_ERROR) + " (TraceId: " + traceId + ")";
        Result<Void> r = Result.failed(BizErrorCode.INTERNAL_ERROR.getCode(), message);
        r.setTraceId(traceId);
        return r;
    }

    // ==================== i18n 辅助方法 ====================

    /**
     * 根据业务错误码解析国际化消息
     *
     * <p>使用 {@link BizErrorCode#getMessageKey()} 作为 key，
     * 当前请求 Locale（由 {@link LocaleContextHolder} 提供）解析消息。
     * 当 messageSource 不可用或未找到 key 时，回退到 {@link BizErrorCode#getMessage()} 默认中文消息。
     *
     * @param errorCode 业务错误码
     * @return 解析后的本地化消息
     */
    private String resolveMessage(BizErrorCode errorCode) {
        if (messageSource == null) {
            return errorCode.getMessage();
        }
        return messageSource.getMessage(errorCode.getMessageKey(), null, errorCode.getMessage(), LocaleContextHolder.getLocale());
    }

    /**
     * 根据 key 与参数解析国际化消息
     *
     * @param key            消息 key
     * @param args           占位符参数
     * @param defaultMessage 默认消息（messageSource 不可用时回退）
     * @return 解析后的本地化消息
     */
    private String resolveMessage(String key, Object[] args, String defaultMessage) {
        if (messageSource == null) {
            return defaultMessage;
        }
        return messageSource.getMessage(key, args, defaultMessage, LocaleContextHolder.getLocale());
    }

    /**
     * 根据错误码数值查找对应的 {@link BizErrorCode}
     *
     * @param code 业务错误码数值
     * @return 匹配的枚举值，未匹配返回 null
     */
    private BizErrorCode findErrorCode(int code) {
        for (BizErrorCode ec : BizErrorCode.values()) {
            if (ec.getCode() == code) {
                return ec;
            }
        }
        return null;
    }
}
