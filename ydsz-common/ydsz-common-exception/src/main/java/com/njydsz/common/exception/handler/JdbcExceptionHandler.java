package com.njydsz.common.exception.handler;

import jakarta.servlet.http.HttpServletRequest;

import java.sql.BatchUpdateException;
import java.sql.SQLException;

import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.exception.code.CoreExceptionCode;
import com.njydsz.common.exception.config.ExceptionProperties;
import com.njydsz.common.exception.core.ExceptionInfo;
import com.njydsz.common.exception.metrics.ExceptionMetrics;

import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.MessageSource;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * JDBC 数据访问异常处理器
 *
 * <p>仅在 spring-jdbc 存在且为 Servlet Web 应用时注册，处理 DataAccessException 及其子类异常。
 *
 * <p><b>装配条件：</b>
 * <ul>
 *   <li>类路径存在 {@link org.springframework.dao.DataAccessException}</li>
 *   <li>应用类型为 {@link org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type#SERVLET}</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see BaseExceptionHandler
 */
@Slf4j
@RestControllerAdvice
@ConditionalOnClass(name = "org.springframework.dao.DataAccessException")
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class JdbcExceptionHandler extends BaseExceptionHandler {

    /** 数据库错误兜底文案（i18n key 解析失败时使用） */
    private static final String DEFAULT_DATABASE_ERROR_MESSAGE = "数据库操作失败";

    private final MessageSource messageSource;

    /**
     * 构造 JDBC 异常处理器
     *
     * @param environment       Spring 环境对象
     * @param messageSource     国际化消息源
     * @param exceptionMetrics  异常指标统计器
     * @param properties       异常模块配置属性（可为 null）
     */
    public JdbcExceptionHandler(Environment environment,
                               MessageSource messageSource,
                               ExceptionMetrics exceptionMetrics,
                               ExceptionProperties properties) {
        super(environment);
        this.messageSource = messageSource;
        setMessageSource(messageSource);
        setExceptionMetrics(environment, exceptionMetrics);
        setExceptionProperties(environment, properties);
    }

    @Override
    protected String getLogPrefix() {
        return "【JDBC】";
    }

    /**
     * 处理数据完整性违反异常（细粒度分类）
     *
     * <p>通过 SQLState 精确匹配分类，返回 HTTP 409 Conflict。
     * SQLState 23xxx 对应 Integrity Constraint Violation：
     * <ul>
     *   <li>23505 / 23000：唯一约束冲突</li>
     *   <li>23503：外键约束冲突</li>
     *   <li>23502：非空约束违反</li>
     *   <li>23514 / 23001：检查约束违反</li>
     * </ul>
     *
     * <p>当 SQLState 不可用时，回退到消息文本匹配。
     *
     * @param e       数据完整性违反异常
     * @param request HTTP 请求
     * @return 统一错误响应，HTTP 状态码 409
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public BaseResponse<?> handleDataIntegrityViolationException(
            DataIntegrityViolationException e, HttpServletRequest request) {
        recordMetrics(e);

        Throwable rootCause = getRootCause(e);
        String rootMessage = rootCause != null ? rootCause.getMessage() : e.getMessage();
        String sqlState = extractSqlState(rootCause);

        CoreExceptionCode exceptionCode;
        String messageKey;

        if (sqlState != null) {
            String classification = classifyBySqlState(sqlState);
            messageKey = classification;
            exceptionCode = codeFromMessageKey(classification);
        } else {
            messageKey = classifyByMessage(rootMessage);
            exceptionCode = codeFromMessageKey(messageKey);
        }

        String message = resolveMessage(messageKey, null, messageKey);
        log.error("{}数据完整性异常 | 路径: {} | SQLState: {} | 消息: {}",
                getLogPrefix(), request.getRequestURI(), sqlState, rootMessage, e);

        return buildWithInfo(
                exceptionCode.getCode(), messageKey, message,
                HttpStatus.CONFLICT.value(), request.getRequestURI());
    }

    /**
     * 处理通用数据访问异常（非完整性违反）
     *
     * @param e       数据访问异常
     * @param request HTTP 请求
     * @return 统一错误响应
     */
    @ExceptionHandler(DataAccessException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public BaseResponse<?> handleDataAccessException(
            DataAccessException e, HttpServletRequest request) {
        recordMetrics(e);
        log.error("{}数据访问异常 | 路径: {} | 消息: {}", getLogPrefix(), request.getRequestURI(), e.getMessage(), e);

        // 通过 MessageSource 按当前请求 Locale 解析 i18n 文案，避免向客户端暴露裸 key
        String message = resolveMessage(
                CoreExceptionCode.DATABASE_ERROR.getKey(), null,
                DEFAULT_DATABASE_ERROR_MESSAGE);
        ExceptionInfo info = buildExceptionInfo(e, request.getRequestURI(), extractTraceId(request));
        info.setCode(CoreExceptionCode.DATABASE_ERROR.getCode());
        info.setMessage(message);

        return errorResponse(
                CoreExceptionCode.DATABASE_ERROR.getCode(),
                message,
                includeExceptionInfo() ? info : null
        );
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 沿异常链追溯根本原因
     *
     * @param throwable 异常对象
     * @return 根本原因异常
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
     * 通过 SQLState 分类（精确匹配）
     *
     * @param sqlState SQLState 字符串
     * @return 对应的消息 key
     */
    private String classifyBySqlState(String sqlState) {
        return switch (sqlState) {
            case "23505", "23000" -> CoreExceptionCode.UNIQUE_CONSTRAINT_VIOLATION.getKey();
            case "23503" -> CoreExceptionCode.FOREIGN_KEY_VIOLATION.getKey();
            case "23502" -> CoreExceptionCode.NOT_NULL_VIOLATION.getKey();
            case "23514", "23001" -> CoreExceptionCode.CHECK_CONSTRAINT_VIOLATION.getKey();
            default -> {
                if (sqlState.startsWith("23")) {
                    yield CoreExceptionCode.CONFLICT.getKey();
                }
                // SQLState 不在已知范围，回退到消息匹配
                yield classifyByMessage(null);
            }
        };
    }

    /**
     * 通过消息文本回退分类（当 SQLState 不可用时使用）
     *
     * @param message 异常消息
     * @return 消息 key
     */
    private String classifyByMessage(String message) {
        if (message == null) {
            return CoreExceptionCode.CONFLICT.getKey();
        }
        String lower = message.toLowerCase();
        if (lower.contains("duplicate") || lower.contains("unique")) {
            return CoreExceptionCode.UNIQUE_CONSTRAINT_VIOLATION.getKey();
        }
        if (lower.contains("foreign key") || lower.contains("referential")) {
            return CoreExceptionCode.FOREIGN_KEY_VIOLATION.getKey();
        }
        if (lower.contains("not-null") || lower.contains("cannot be null")) {
            return CoreExceptionCode.NOT_NULL_VIOLATION.getKey();
        }
        if (lower.contains("check constraint") || lower.contains("violates")) {
            return CoreExceptionCode.CHECK_CONSTRAINT_VIOLATION.getKey();
        }
        return CoreExceptionCode.CONFLICT.getKey();
    }

    /**
     * 将消息 key 映射到对应的 {@link CoreExceptionCode}
     *
     * @param messageKey 消息 key
     * @return 对应异常码枚举
     */
    private CoreExceptionCode codeFromMessageKey(String messageKey) {
        if (messageKey == null) {
            return CoreExceptionCode.CONFLICT;
        }
        for (CoreExceptionCode code : CoreExceptionCode.values()) {
            if (code.getKey().equals(messageKey)) {
                return code;
            }
        }
        return CoreExceptionCode.CONFLICT;
    }
}
