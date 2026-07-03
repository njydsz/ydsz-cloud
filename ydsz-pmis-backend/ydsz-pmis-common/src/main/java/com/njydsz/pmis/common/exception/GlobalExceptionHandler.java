package com.njydsz.pmis.common.exception;

import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.common.util.TraceIdUtil;
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
     * <p>使用构造器注入并通过 {@link ObjectProvider} 支持缺失场景，
     * 便于单元测试中通过反射或子类化绕过 Spring 容器；
     * 当 messageSource 为 null（如单元测试）时，回退到 {@link BizErrorCode#getMessage()} 默认中文消息。
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

    // ==================== H9.1 修复：数据库异常处理 ====================

    /**
     * 唯一键冲突处理（PostgreSQL SQLSTATE 23505）
     *
     * <p>典型场景：插入重复业务编号（如 contract_no、invoice_no、user_username）。
     * 区别于 BizErrorCode.DUPLICATE_KEY（业务层校验），DB_DUPLICATE_KEY 是数据库约束层面触发。
     *
     * @param e   唯一键冲突异常
     * @param req HTTP 请求
     * @return 统一响应（400 BAD_REQUEST）
     */
    @ExceptionHandler(DuplicateKeyException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleDuplicateKey(DuplicateKeyException e, HttpServletRequest req) {
        String detail = extractPgDetail(e.getMessage());
        log.warn("[DB-DuplicateKey] {} {} - {}", req.getMethod(), req.getRequestURI(), detail);
        Result<Void> r = Result.failed(BizErrorCode.DB_DUPLICATE_KEY.getCode(),
                resolveMessage(BizErrorCode.DB_DUPLICATE_KEY) + (detail != null ? ": " + detail : ""));
        r.setTraceId(TraceIdUtil.get());
        return r;
    }

    /**
     * 数据完整性约束冲突处理（PostgreSQL SQLSTATE 23xxx 系列）
     *
     * <p>包括：外键冲突（23503）、非空约束（23502）、CHECK 约束（23514）等。
     *
     * @param e   数据完整性异常
     * @param req HTTP 请求
     * @return 统一响应（400 BAD_REQUEST）
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<Void> handleDataIntegrity(DataIntegrityViolationException e, HttpServletRequest req) {
        // DuplicateKeyException 是 DataIntegrityViolationException 的子类，已被上面更具体的 handler 处理
        if (e instanceof DuplicateKeyException) {
            return handleDuplicateKey((DuplicateKeyException) e, req);
        }
        String detail = extractPgDetail(e.getMessage());
        log.warn("[DB-DataIntegrity] {} {} - {}", req.getMethod(), req.getRequestURI(), detail);
        Result<Void> r = Result.failed(BizErrorCode.DB_DATA_INTEGRITY.getCode(),
                resolveMessage(BizErrorCode.DB_DATA_INTEGRITY) + (detail != null ? ": " + detail : ""));
        r.setTraceId(TraceIdUtil.get());
        return r;
    }

    /**
     * 乐观锁冲突处理（@Version 版本号不匹配）
     *
     * <p>典型场景：并发更新同一资源，MyBatis-Plus @Version 机制触发。
     *
     * @param e   乐观锁失败异常
     * @param req HTTP 请求
     * @return 统一响应（409 CONFLICT）
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Result<Void> handleOptimisticLock(OptimisticLockingFailureException e, HttpServletRequest req) {
        log.warn("[DB-OptimisticLock] {} {} - {}", req.getMethod(), req.getRequestURI(), e.getMessage());
        // 乐观锁冲突映射到 DB_LOCK_CONTENTION，提示用户"数据已被他人修改，请刷新后重试"
        Result<Void> r = Result.failed(BizErrorCode.DB_LOCK_CONTENTION.getCode(),
                resolveMessage(BizErrorCode.DB_LOCK_CONTENTION) + "：数据已被他人修改，请刷新后重试");
        r.setTraceId(TraceIdUtil.get());
        return r;
    }

    /**
     * 数据库查询超时处理
     *
     * @param e   查询超时异常
     * @param req HTTP 请求
     * @return 统一响应（503 SERVICE_UNAVAILABLE）
     */
    @ExceptionHandler(QueryTimeoutException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public Result<Void> handleQueryTimeout(QueryTimeoutException e, HttpServletRequest req) {
        log.warn("[DB-QueryTimeout] {} {} - {}", req.getMethod(), req.getRequestURI(), e.getMessage());
        Result<Void> r = Result.failed(BizErrorCode.DB_QUERY_TIMEOUT.getCode(),
                resolveMessage(BizErrorCode.DB_QUERY_TIMEOUT));
        r.setTraceId(TraceIdUtil.get());
        return r;
    }

    /**
     * 数据库连接失败处理（连接池耗尽、网络断开等）
     *
     * <p>TransientDataAccessResourceException 在 Spring 中表示"暂时性数据访问资源不可用"，
     * 常见于数据库连接池耗尽或 PG 主从切换期间。
     *
     * @param e   暂时性数据访问异常
     * @param req HTTP 请求
     * @return 统一响应（503 SERVICE_UNAVAILABLE）
     */
    @ExceptionHandler(TransientDataAccessResourceException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public Result<Void> handleDbConnFailed(TransientDataAccessResourceException e, HttpServletRequest req) {
        log.error("[DB-ConnFailed] {} {} - {}", req.getMethod(), req.getRequestURI(), e.getMessage());
        Result<Void> r = Result.failed(BizErrorCode.DB_CONNECTION_FAILED.getCode(),
                resolveMessage(BizErrorCode.DB_CONNECTION_FAILED));
        r.setTraceId(TraceIdUtil.get());
        return r;
    }

    /**
     * DataAccessException 兜底（捕获所有未被上面具体 handler 命中的 DAO 异常）
     *
     * <p>例如：BadSqlGrammarException（SQL 语法错误，通常是 mapper 配置问题）、
     * IncorrectResultSizeDataAccessException 等。
     *
     * @param e   DAO 异常
     * @param req HTTP 请求
     * @return 统一响应（500 INTERNAL_SERVER_ERROR）
     */
    @ExceptionHandler(DataAccessException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Result<Void> handleDataAccessException(DataAccessException e, HttpServletRequest req) {
        String traceId = TraceIdUtil.get();
        log.error("[DB-Error] {} {} - traceId={} - {}", req.getMethod(), req.getRequestURI(), traceId, e.getMessage());
        // H9.3 修复：不向客户端暴露 SQL 细节，仅返回 traceId 供排查
        String message = resolveMessage(BizErrorCode.INTERNAL_ERROR) + " (TraceId: " + traceId + ")";
        Result<Void> r = Result.failed(BizErrorCode.INTERNAL_ERROR.getCode(), message);
        r.setTraceId(traceId);
        return r;
    }

    /**
     * 从 PostgreSQL 异常消息中提取 Detail 字段，避免暴露完整堆栈。
     *
     * <p>PG 异常消息通常形如：{@code ERROR: duplicate key value violates unique constraint "uk_contract_no"
     * Detail: Key (contract_no)=(HT20260101001) already exists.}
     * 本方法提取 "Detail:" 之后的内容用于业务提示。
     *
     * @param msg 原始异常消息
     * @return 提取后的 Detail 内容，无匹配则返回 null
     */
    private String extractPgDetail(String msg) {
        if (msg == null) return null;
        int idx = msg.indexOf("Detail:");
        if (idx < 0) return null;
        return msg.substring(idx + 7).trim();
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
