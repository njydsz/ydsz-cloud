package com.njydsz.pmis.common.exception;

import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.api.R;
import com.njydsz.pmis.common.util.TraceIdUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
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
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 业务异常
     */
    @ExceptionHandler(BizException.class)
    public R<Void> handleBizException(BizException e, HttpServletRequest req) {
        log.warn("[BizException] {} {} - code={} message={}",
                req.getMethod(), req.getRequestURI(), e.getCode(), e.getMessage());
        R<Void> r = R.failed(e.getCode(), e.getErrorMessage());
        r.setTraceId(TraceIdUtil.get());
        return r;
    }

    /**
     * @Valid 校验失败 (RequestBody)
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.OK)
    public R<Void> handleValidException(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("[ValidationFailed] {}", msg);
        R<Void> r = R.failed(BizErrorCode.VALIDATION_FAILED.getCode(), msg);
        r.setTraceId(TraceIdUtil.get());
        return r;
    }

    /**
     * @Valid 校验失败 (Form)
     */
    @ExceptionHandler(BindException.class)
    public R<Void> handleBindException(BindException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("[BindException] {}", msg);
        R<Void> r = R.failed(BizErrorCode.VALIDATION_FAILED.getCode(), msg);
        r.setTraceId(TraceIdUtil.get());
        return r;
    }

    /**
     * @Validated 校验失败 (Path/Param)
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public R<Void> handleConstraintViolation(ConstraintViolationException e) {
        String msg = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining("; "));
        log.warn("[ConstraintViolation] {}", msg);
        R<Void> r = R.failed(BizErrorCode.VALIDATION_FAILED.getCode(), msg);
        r.setTraceId(TraceIdUtil.get());
        return r;
    }

    /**
     * 缺少参数
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public R<Void> handleMissingParam(MissingServletRequestParameterException e) {
        String msg = String.format("缺少必填参数: %s", e.getParameterName());
        R<Void> r = R.failed(BizErrorCode.MISSING_PARAMETER.getCode(), msg);
        r.setTraceId(TraceIdUtil.get());
        return r;
    }

    /**
     * 请求体解析失败
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public R<Void> handleNotReadable(HttpMessageNotReadableException e) {
        log.warn("[HttpMessageNotReadable] {}", e.getMessage());
        R<Void> r = R.failed(BizErrorCode.BAD_REQUEST);
        r.setTraceId(TraceIdUtil.get());
        return r;
    }

    /**
     * 请求方法不允许
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public R<Void> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        R<Void> r = R.failed(BizErrorCode.METHOD_NOT_ALLOWED);
        r.setTraceId(TraceIdUtil.get());
        return r;
    }

    /**
     * 资源不存在
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public R<Void> handleNotFound(NoHandlerFoundException e) {
        R<Void> r = R.failed(BizErrorCode.NOT_FOUND);
        r.setTraceId(TraceIdUtil.get());
        return r;
    }

    /**
     * 非法参数
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public R<Void> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("[IllegalArgument] {}", e.getMessage());
        R<Void> r = R.failed(BizErrorCode.BAD_REQUEST.getCode(), e.getMessage());
        r.setTraceId(TraceIdUtil.get());
        return r;
    }

    /**
     * 兜底异常
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public R<Void> handleException(Exception e, HttpServletRequest req) {
        log.error("[SystemError] {} {}", req.getMethod(), req.getRequestURI(), e);
        R<Void> r = R.failed(BizErrorCode.INTERNAL_ERROR);
        r.setTraceId(TraceIdUtil.get());
        return r;
    }
}
