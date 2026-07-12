package com.njydsz.pmis.common.exception.handler;

import com.njydsz.pmis.common.core.response.BaseResponse;
import com.njydsz.pmis.common.exception.core.ExceptionInfo;
import com.njydsz.pmis.common.exception.code.UnifiedExceptionCode;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * JDBC 数据访问异常处理器
 *
 * <p>仅在 spring-jdbc 存在时注册，处理 DataAccessException 及其子类异常。
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 3.0.0
 */
@Slf4j
@ConditionalOnClass(name = "org.springframework.dao.DataAccessException")
public class JdbcExceptionHandler extends BaseExceptionHandler {

    private final MessageSource messageSource;

    public JdbcExceptionHandler(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    @Override
    protected String getLogPrefix() {
        return "【JDBC】";
    }

    /**
     * 处理数据访问异常
     *
     * @param e       数据访问异常
     * @param request HTTP 请求
     * @return 统一错误响应
     */
    @ExceptionHandler(DataAccessException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public BaseResponse<?> handleDataAccessException(
            DataAccessException e, HttpServletRequest request) {
        log.error("{}数据访问异常 | 路径: {} | 消息: {}", getLogPrefix(), request.getRequestURI(), e.getMessage(), e);

        ExceptionInfo info = buildExceptionInfo(e, request.getRequestURI(), extractTraceId(request));
        info.setCode(UnifiedExceptionCode.DATABASE_ERROR.getCode());
        String message = messageSource.getMessage(UnifiedExceptionCode.DATABASE_ERROR.getKey(), null,
                UnifiedExceptionCode.DATABASE_ERROR.getKey(), LocaleContextHolder.getLocale());
        info.setMessage(message != null ? message : UnifiedExceptionCode.DATABASE_ERROR.getKey());

        return BaseResponse.error(
                UnifiedExceptionCode.DATABASE_ERROR.getCode(),
                message,
                includeExceptionInfo() ? info : null
        );
    }

    /**
     * 从 HttpServletRequest 提取 traceId
     */
    private String extractTraceId(HttpServletRequest request) {
        String traceId = request.getHeader("X-Trace-Id");
        if (traceId == null) {
            traceId = request.getHeader(com.njydsz.pmis.common.core.constant.HeaderConstants.X_REQUEST_ID);
        }
        return traceId;
    }
}
