package com.njydsz.common.jdbc.exception;

import jakarta.servlet.http.HttpServletRequest;

import java.sql.BatchUpdateException;
import java.sql.SQLException;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.exception.code.CoreExceptionCode;
import com.njydsz.common.exception.core.ExceptionInfo;

import lombok.extern.slf4j.Slf4j;

/**
 * 数据完整性异常处理器
 *
 * <p>捕获并处理数据库约束违反异常（如唯一约束、外键约束等），
 * 返回友好的错误提示，避免暴露底层数据库异常信息。
 *
 * <p><b>处理的异常类型：</b>
 * <ul>
 *   <li>Duplicate key - 唯一约束冲突，返回"数据已存在"提示</li>
 *   <li>Foreign key constraint - 外键约束冲突，返回"关联数据存在"提示</li>
 *   <li>其他数据完整性异常 - 返回通用冲突提示</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@RestControllerAdvice
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(name = "org.springframework.dao.DataIntegrityViolationException")
@ConditionalOnMissingBean(DataIntegrityExceptionHandler.class)
public class DataIntegrityExceptionHandler {

    private final MessageSource messageSource;

    public DataIntegrityExceptionHandler(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    /**
     * 处理数据完整性违反异常
     *
     * @param e 数据完整性违反异常
     * @param request HTTP 请求对象
     * @return 统一错误响应，HTTP 状态码为 409 Conflict
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public BaseResponse<?> handleDataIntegrityViolationException(
            DataIntegrityViolationException e, HttpServletRequest request) {
        Throwable rootCause = getRootCause(e);
        String rootMessage = rootCause != null ? rootCause.getMessage() : e.getMessage();

        // 使用 SQLState 精确匹配，避免字符串匹配的脆弱性
        // SQLState 23xxx 对应 Integrity Constraint Violation
        //   23000: generic constraint violation
        //   23001: cannot insert null
        //   23502: not-null violation
        //   23503: foreign key violation
        //   23505: unique constraint violation (duplicate key)
        //   23514: check constraint violation (PostgreSQL specific)
        String sqlState = extractSqlState(rootCause);
        String messageKey;
        CoreExceptionCode exceptionCode;

        if (sqlState != null) {
            switch (sqlState) {
                case "23505", "23000" -> {
                    messageKey = "data.integrity.duplicate";
                    exceptionCode = CoreExceptionCode.UNIQUE_CONSTRAINT_VIOLATION;
                }
                case "23503" -> {
                    messageKey = "data.integrity.foreign.key";
                    exceptionCode = CoreExceptionCode.FOREIGN_KEY_VIOLATION;
                }
                case "23502" -> {
                    messageKey = "data.integrity.not.null";
                    exceptionCode = CoreExceptionCode.NOT_NULL_VIOLATION;
                }
                case "23514", "23001" -> {
                    messageKey = "data.integrity.check";
                    exceptionCode = CoreExceptionCode.CHECK_CONSTRAINT_VIOLATION;
                }
                default -> {
                    if (sqlState.startsWith("23")) {
                        messageKey = "data.integrity.conflict";
                        exceptionCode = CoreExceptionCode.CONFLICT;
                    } else {
                        // SQLState 不在已知范围，回退到消息匹配
                        messageKey = classifyByMessage(rootMessage);
                        exceptionCode = classifyByMessageToCode(messageKey);
                    }
                }
            }
        } else {
            // SQLState 不可用时，回退到消息匹配（兼容性保证）
            messageKey = classifyByMessage(rootMessage);
            exceptionCode = classifyByMessageToCode(messageKey);
        }

        String message = messageSource.getMessage(messageKey, null, messageKey, LocaleContextHolder.getLocale());
        log.error("数据完整性异常 | 路径: {} | 消息: {}", request.getRequestURI(), rootMessage, e);

        ExceptionInfo info = new ExceptionInfo(
                exceptionCode.getCode(),
                exceptionCode.getKey(),
                message,
                HttpStatus.CONFLICT.value()
        );
        info.setPath(request.getRequestURI());
        return BaseResponse.<ExceptionInfo>builder()
                .code(exceptionCode.getCode())
                .msg(message)
                .data(info)
                .timestamp(System.currentTimeMillis())
                .build();
    }

    /**
     * 获取异常的根本原因，沿异常链逐层追溯直到最底层原因
     *
     * @param throwable 异常对象
     * @return 根本原因异常，无原因链时返回自身
     */
    private Throwable getRootCause(Throwable throwable) {
        Throwable cause = throwable;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return cause;
    }

    /**
     * 从异常链中提取 SQLState
     *
     * @param throwable 异常对象
     * @return SQLState 字符串，不可用时返回 null
     */
    private String extractSqlState(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof SQLException sqlException) {
                String sqlState = sqlException.getSQLState();
                if (sqlState != null && !sqlState.isEmpty()) {
                    return sqlState;
                }
            }
            if (current instanceof BatchUpdateException batchException) {
                String sqlState = batchException.getSQLState();
                if (sqlState != null && !sqlState.isEmpty()) {
                    return sqlState;
                }
            }
            current = current.getCause();
        }
        return null;
    }

    /**
     * 通过消息文本回退分类（当 SQLState 不可用时使用）
     *
     * @param message 异常消息
     * @return 消息 key
     */
    private String classifyByMessage(String message) {
        if (message == null) {
            return "data.integrity.conflict";
        }
        String lower = message.toLowerCase();
        if (lower.contains("duplicate") || lower.contains("unique")) {
            return "data.integrity.duplicate";
        }
        if (lower.contains("foreign key") || lower.contains("referential")) {
            return "data.integrity.foreign.key";
        }
        if (lower.contains("not-null") || lower.contains("cannot be null")) {
            return "data.integrity.not.null";
        }
        if (lower.contains("check constraint") || lower.contains("violates")) {
            return "data.integrity.check";
        }
        return "data.integrity.conflict";
    }

    /**
     * 将回退分类得到的消息 key 映射到对应的 {@link CoreExceptionCode}。
     *
     * <p>保持与 {@link #classifyByMessage(String)} 分类逻辑一致，用于
     * {@code ExceptionInfo} 和 {@code BaseResponse} 的错误码填充。
     *
     * @param messageKey 消息 key
     * @return 对应的异常码枚举
     */
    private CoreExceptionCode classifyByMessageToCode(String messageKey) {
        if (messageKey == null) {
            return CoreExceptionCode.CONFLICT;
        }
        return switch (messageKey) {
            case "data.integrity.duplicate" -> CoreExceptionCode.UNIQUE_CONSTRAINT_VIOLATION;
            case "data.integrity.foreign.key" -> CoreExceptionCode.FOREIGN_KEY_VIOLATION;
            case "data.integrity.not.null" -> CoreExceptionCode.NOT_NULL_VIOLATION;
            case "data.integrity.check" -> CoreExceptionCode.CHECK_CONSTRAINT_VIOLATION;
            default -> CoreExceptionCode.CONFLICT;
        };
    }
}
