package com.njydsz.pmis.common.jdbc.exception;

import com.njydsz.pmis.common.exception.code.UnifiedExceptionCode;
import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.common.exception.ExceptionInfo;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
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
 * @author ydsz-pmis-team
 * 
 * 
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
    public Result<?> handleDataIntegrityViolationException(
            DataIntegrityViolationException e, HttpServletRequest request) {
        Throwable rootCause = getRootCause(e);
        String rootMessage = rootCause != null ? rootCause.getMessage() : e.getMessage();

        String messageKey;
        if (rootMessage != null && rootMessage.contains("Duplicate")) {
            messageKey = "data.integrity.duplicate";
        } else if (rootMessage != null && rootMessage.contains("foreign key")) {
            messageKey = "data.integrity.foreign.key";
        } else {
            messageKey = "data.integrity.conflict";
        }

        String message = messageSource.getMessage(messageKey, null, messageKey, LocaleContextHolder.getLocale());
        log.error("数据完整性异常 | 路径: {} | 消息: {}", request.getRequestURI(), rootMessage, e);

        ExceptionInfo info = new ExceptionInfo(
                UnifiedExceptionCode.UNIQUE_CONSTRAINT_VIOLATION.getCode(),
                UnifiedExceptionCode.UNIQUE_CONSTRAINT_VIOLATION.getKey(),
                message,
                HttpStatus.CONFLICT.value()
        );
        info.setPath(request.getRequestURI());
        return Result.error(
                UnifiedExceptionCode.UNIQUE_CONSTRAINT_VIOLATION.getCode(),
                message,
                info
        );
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
}
