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
     * 业务异常处理
     *
     * @param e   业务异常
     * @param req HTTP 请求
     * @return 统一响应
     */
    @ExceptionHandler(BizException.class)
    public R<Void> handleBizException(BizException e, HttpServletRequest req) {
        log.warn("[BizException] {} {} - code={} message={}",
                req.getMethod(), req.getRequestURI(), e.getCode(), e.getMessage());
        R<Void> r = R.failed(e.getCode(), e.getErrorMessage());
        Result.setTraceId(TraceIdUtil.get());
        return r;
    }

    /**
     * @Valid 校验失败 (RequestBody) 处理
     *
     * @param e 校验异常
     * @return 统一响应
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.OK)
    public R<Void> handleValidException(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("[ValidationFailed] {}", msg);
        R<Void> r = R.failed(BizErrorCode.VALIDATION_FAILED.getCode(), msg);
        Result.setTraceId(TraceIdUtil.get());
        return r;
    }

    /**
     * @Valid 校验失败 (Form) 处理
     *
     * @param e 绑定异常
     * @return 统一响应
     */
    @ExceptionHandler(BindException.class)
    public R<Void> handleBindException(BindException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("[BindException] {}", msg);
        R<Void> r = R.failed(BizErrorCode.VALIDATION_FAILED.getCode(), msg);
        Result.setTraceId(TraceIdUtil.get());
        return r;
    }

    /**
     * @Validated 校验失败 (Path/Param) 处理
     *
     * @param e 约束违反异常
     * @return 统一响应
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public R<Void> handleConstraintViolation(ConstraintViolationException e) {
        String msg = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining("; "));
        log.warn("[ConstraintViolation] {}", msg);
        R<Void> r = R.failed(BizErrorCode.VALIDATION_FAILED.getCode(), msg);
        Result.setTraceId(TraceIdUtil.get());
        return r;
    }

    /**
     * 缺少必填参数处理
     *
     * @param e 缺少参数异常
     * @return 统一响应
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public R<Void> handleMissingParam(MissingServletRequestParameterException e) {
        String msg = String.format("缺少必填参数: %s", e.getParameterName());
        R<Void> r = R.failed(BizErrorCode.MISSING_PARAMETER.getCode(), msg);
        Result.setTraceId(TraceIdUtil.get());
        return r;
    }

    /**
     * 请求体解析失败处理
     *
     * @param e 请求体不可读异常
     * @return 统一响应
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public R<Void> handleNotReadable(HttpMessageNotReadableException e) {
        log.warn("[HttpMessageNotReadable] {}", e.getMessage());
        R<Void> r = R.failed(BizErrorCode.BAD_REQUEST);
        Result.setTraceId(TraceIdUtil.get());
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
    public R<Void> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        R<Void> r = R.failed(BizErrorCode.METHOD_NOT_ALLOWED);
        Result.setTraceId(TraceIdUtil.get());
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
    public R<Void> handleNotFound(NoHandlerFoundException e) {
        R<Void> r = R.failed(BizErrorCode.NOT_FOUND);
        Result.setTraceId(TraceIdUtil.get());
        return r;
    }

    /**
     * 非法参数处理
     *
     * @param e 非法参数异常
     * @return 统一响应
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public R<Void> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("[IllegalArgument] {}", e.getMessage());
        R<Void> r = R.failed(BizErrorCode.BAD_REQUEST.getCode(), e.getMessage());
        Result.setTraceId(TraceIdUtil.get());
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
    public R<Void> handleException(Exception e, HttpServletRequest req) {
        log.error("[SystemError] {} {}", req.getMethod(), req.getRequestURI(), e);
        R<Void> r = R.failed(BizErrorCode.INTERNAL_ERROR);
        Result.setTraceId(TraceIdUtil.get());
        return r;
    }
}
