package com.njydsz.pmis.common.exception;

import com.njydsz.pmis.common.core.response.BaseResponse;
import com.njydsz.pmis.common.core.response.StandardResultCode;
import org.slf4j.MDC;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

import java.util.Map;
import java.util.stream.Collectors;

/**
 * 全局异常处理
 *
 * <p>统一处理 Controller 层异常，转换为 BaseResponse 格式返回。
 *
 * <p>i18n 支持：通过 {@link MessageSource} 根据 Accept-Language 请求头解析本地化消息。
 * 当 {@code BizException} 仅由 {@code StandardResultCode} 构造（无自定义 message）时，
 * 使用 {@code error.{ENUM_NAME}} key 解析消息；当存在自定义 message 时直接使用该 message。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * StandardResultCode 快速查找索引（O(1) HashMap 查找）。
     *
     * <p>类加载时一次性构建，线程安全（Map.of 返回不可变 Map）。
     */
    private static final Map<String, StandardResultCode> ERROR_CODE_INDEX =
            java.util.Arrays.stream(StandardResultCode.values())
                    .collect(Collectors.toUnmodifiableMap(
                            StandardResultCode::getCode,
                            ec -> ec,
                            (existing, replacement) -> existing));

    /**
     * 国际化消息源（可选注入）。
     */
    private final MessageSource messageSource;

    /**
     * 构造器：通过 {@link ObjectProvider} 支持 {@link MessageSource} 可选注入。
     *
     * @param messageSourceProvider 国际化消息源提供者（可选）
     */
    public GlobalExceptionHandler(ObjectProvider<MessageSource> messageSourceProvider) {
        this.messageSource = messageSourceProvider.getIfAvailable();
    }

    /**
     * 业务异常处理
     *
     * @param e   业务异常
     * @param req HTTP 请求
     * @return 统一响应（携带 HTTP 状态码）
     */
    @ExceptionHandler(BizException.class)
    public ResponseEntity<BaseResponse<Void>> handleBizException(BizException e, HttpServletRequest req) {
        log.warn("[BizException] {} {} - code={} message={}",
                req.getMethod(), req.getRequestURI(), e.getCode(), e.getMessage());
        String message = e.getErrorMessage();
        StandardResultCode errorCode = findErrorCode(e.getCode());
        if (errorCode != null && errorCode.getMsg().equals(message)) {
            message = resolveMessage(errorCode);
        } else if (message != null && message.startsWith("error.")) {
            message = resolveMessage(message, e.getArgs(), message);
        }
        BaseResponse<Void> r = BaseResponse.failed(e.getCode(), message);
        r.setTraceId(MDC.get("traceId"));
        HttpStatus status = errorCode != null ? errorCode.getHttpStatus() : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(r);
    }

    /**
     * @Valid 校验失败 (RequestBody) 处理
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public BaseResponse<Void> handleValidException(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("[ValidationFailed] {}", msg);
        BaseResponse<Void> r = BaseResponse.failed(StandardResultCode.VALIDATION_FAILED.getCode(), msg);
        r.setTraceId(MDC.get("traceId"));
        return r;
    }

    /**
     * @Valid 校验失败 (Form) 处理
     */
    @ExceptionHandler(BindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public BaseResponse<Void> handleBindException(BindException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("[BindException] {}", msg);
        BaseResponse<Void> r = BaseResponse.failed(StandardResultCode.VALIDATION_FAILED.getCode(), msg);
        r.setTraceId(MDC.get("traceId"));
        return r;
    }

    /**
     * @Validated 校验失败 (Path/Param) 处理
     */
    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public BaseResponse<Void> handleConstraintViolation(ConstraintViolationException e) {
        String msg = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining("; "));
        log.warn("[ConstraintViolation] {}", msg);
        BaseResponse<Void> r = BaseResponse.failed(StandardResultCode.VALIDATION_FAILED.getCode(), msg);
        r.setTraceId(MDC.get("traceId"));
        return r;
    }

    /**
     * 缺少必填参数处理
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public BaseResponse<Void> handleMissingParam(MissingServletRequestParameterException e) {
        String msg = resolveMessage("error.missing_parameter",
                new Object[]{e.getParameterName()},
                String.format("缺少必填参数: %s", e.getParameterName()));
        BaseResponse<Void> r = BaseResponse.failed(StandardResultCode.MISSING_PARAMETER.getCode(), msg);
        r.setTraceId(MDC.get("traceId"));
        return r;
    }

    /**
     * 请求体解析失败处理
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public BaseResponse<Void> handleNotReadable(HttpMessageNotReadableException e) {
        log.warn("[HttpMessageNotReadable] {}", e.getMessage());
        BaseResponse<Void> r = BaseResponse.failed(StandardResultCode.BAD_REQUEST.getCode(), resolveMessage(StandardResultCode.BAD_REQUEST));
        r.setTraceId(MDC.get("traceId"));
        return r;
    }

    /**
     * 请求方法不允许处理
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.METHOD_NOT_ALLOWED)
    public BaseResponse<Void> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        BaseResponse<Void> r = BaseResponse.failed(StandardResultCode.METHOD_NOT_ALLOWED.getCode(), resolveMessage(StandardResultCode.METHOD_NOT_ALLOWED));
        r.setTraceId(MDC.get("traceId"));
        return r;
    }

    /**
     * 资源不存在处理
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public BaseResponse<Void> handleNotFound(NoHandlerFoundException e) {
        BaseResponse<Void> r = BaseResponse.failed(StandardResultCode.NOT_FOUND.getCode(), resolveMessage(StandardResultCode.NOT_FOUND));
        r.setTraceId(MDC.get("traceId"));
        return r;
    }

    /**
     * 非法参数处理
     */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public BaseResponse<Void> handleIllegalArgument(IllegalArgumentException e) {
        log.warn("[IllegalArgument] {}", e.getMessage());
        BaseResponse<Void> r = BaseResponse.failed(StandardResultCode.BAD_REQUEST.getCode(), e.getMessage());
        r.setTraceId(MDC.get("traceId"));
        return r;
    }

    /**
     * 兜底异常处理
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public BaseResponse<Void> handleException(Exception e, HttpServletRequest req) {
        log.error("[SystemError] {} {}", req.getMethod(), req.getRequestURI(), e);
        String traceId = MDC.get("traceId");
        String message = resolveMessage("error.internal_error_with_traceid",
                new Object[]{traceId},
                resolveMessage(StandardResultCode.INTERNAL_ERROR) + " (TraceId: " + traceId + ")");
        BaseResponse<Void> r = BaseResponse.failed(StandardResultCode.INTERNAL_ERROR.getCode(), message);
        r.setTraceId(traceId);
        return r;
    }

    // ==================== 数据库异常处理 ====================

    @ExceptionHandler(DuplicateKeyException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public BaseResponse<Void> handleDuplicateKey(DuplicateKeyException e, HttpServletRequest req) {
        String detail = extractPgDetail(e.getMessage());
        log.warn("[DB-DuplicateKey] {} {} - {}", req.getMethod(), req.getRequestURI(), detail);
        BaseResponse<Void> r = BaseResponse.failed(StandardResultCode.DB_DUPLICATE_KEY.getCode(),
                resolveMessage(StandardResultCode.DB_DUPLICATE_KEY) + (detail != null ? ": " + detail : ""));
        r.setTraceId(MDC.get("traceId"));
        return r;
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public BaseResponse<Void> handleDataIntegrity(DataIntegrityViolationException e, HttpServletRequest req) {
        if (e instanceof DuplicateKeyException) {
            return handleDuplicateKey((DuplicateKeyException) e, req);
        }
        String detail = extractPgDetail(e.getMessage());
        log.warn("[DB-DataIntegrity] {} {} - {}", req.getMethod(), req.getRequestURI(), detail);
        BaseResponse<Void> r = BaseResponse.failed(StandardResultCode.DB_DATA_INTEGRITY.getCode(),
                resolveMessage(StandardResultCode.DB_DATA_INTEGRITY) + (detail != null ? ": " + detail : ""));
        r.setTraceId(MDC.get("traceId"));
        return r;
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public BaseResponse<Void> handleOptimisticLock(OptimisticLockingFailureException e, HttpServletRequest req) {
        log.warn("[DB-OptimisticLock] {} {} - {}", req.getMethod(), req.getRequestURI(), e.getMessage());
        BaseResponse<Void> r = BaseResponse.failed(StandardResultCode.DB_LOCK_CONTENTION.getCode(),
                resolveMessage("error.db_lock_contention_retry",
                        null,
                        resolveMessage(StandardResultCode.DB_LOCK_CONTENTION) + "：数据已被他人修改，请刷新后重试"));
        r.setTraceId(MDC.get("traceId"));
        return r;
    }

    @ExceptionHandler(QueryTimeoutException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public BaseResponse<Void> handleQueryTimeout(QueryTimeoutException e, HttpServletRequest req) {
        log.warn("[DB-QueryTimeout] {} {} - {}", req.getMethod(), req.getRequestURI(), e.getMessage());
        BaseResponse<Void> r = BaseResponse.failed(StandardResultCode.DB_QUERY_TIMEOUT.getCode(),
                resolveMessage(StandardResultCode.DB_QUERY_TIMEOUT));
        r.setTraceId(MDC.get("traceId"));
        return r;
    }

    @ExceptionHandler(TransientDataAccessResourceException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public BaseResponse<Void> handleDbConnFailed(TransientDataAccessResourceException e, HttpServletRequest req) {
        log.error("[DB-ConnFailed] {} {} - {}", req.getMethod(), req.getRequestURI(), e.getMessage());
        BaseResponse<Void> r = BaseResponse.failed(StandardResultCode.DB_CONNECTION_FAILED.getCode(),
                resolveMessage(StandardResultCode.DB_CONNECTION_FAILED));
        r.setTraceId(MDC.get("traceId"));
        return r;
    }

    @ExceptionHandler(DataAccessException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public BaseResponse<Void> handleDataAccessException(DataAccessException e, HttpServletRequest req) {
        String traceId = MDC.get("traceId");
        log.error("[DB-Error] {} {} - traceId={} - {}", req.getMethod(), req.getRequestURI(), traceId, e.getMessage());
        String message = resolveMessage("error.internal_error_with_traceid",
                new Object[]{traceId},
                resolveMessage(StandardResultCode.INTERNAL_ERROR) + " (TraceId: " + traceId + ")");
        BaseResponse<Void> r = BaseResponse.failed(StandardResultCode.INTERNAL_ERROR.getCode(), message);
        r.setTraceId(traceId);
        return r;
    }

    // ==================== 辅助方法 ====================

    private String extractPgDetail(String msg) {
        if (msg == null) return null;
        int idx = msg.indexOf("Detail:");
        if (idx < 0) return null;
        return msg.substring(idx + 7).trim();
    }

    private String resolveMessage(StandardResultCode errorCode) {
        if (messageSource == null) {
            return errorCode.getMsg();
        }
        return messageSource.getMessage(errorCode.getMessageKey(), null, errorCode.getMsg(), LocaleContextHolder.getLocale());
    }

    private String resolveMessage(String key, Object[] args, String defaultMessage) {
        if (messageSource == null) {
            return defaultMessage;
        }
        return messageSource.getMessage(key, args, defaultMessage, LocaleContextHolder.getLocale());
    }

    private StandardResultCode findErrorCode(String code) {
        return ERROR_CODE_INDEX.get(code);
    }
}
