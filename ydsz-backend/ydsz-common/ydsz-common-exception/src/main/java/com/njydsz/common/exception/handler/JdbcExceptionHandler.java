package com.njydsz.common.exception.handler;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

import com.njydsz.common.core.constant.HeaderConstants;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.exception.alert.ExceptionAlertPublisher;
import com.njydsz.common.exception.code.UnifiedExceptionCode;
import com.njydsz.common.exception.config.ExceptionProperties;
import com.njydsz.common.exception.core.ExceptionInfo;
import com.njydsz.common.exception.metrics.ExceptionMetrics;
import com.njydsz.common.exception.observability.TraceContext;

import lombok.extern.slf4j.Slf4j;

/**
 * JDBC 数据访问异常处理器
 *
 * <p>仅在 spring-jdbc 存在时注册，处理 DataAccessException 及其子类异常。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see BaseExceptionHandler
 */
@Slf4j
@ConditionalOnClass(name = "org.springframework.dao.DataAccessException")
public class JdbcExceptionHandler extends BaseExceptionHandler {

    private final MessageSource messageSource;

    /**
     * 构造 JDBC 异常处理器
     *
     * @param messageSource   国际化消息源
     * @param exceptionMetrics 异常指标统计器
     * @param properties      异常模块配置属性
     * @param alertPublisher  异常告警发布器
     */
    public JdbcExceptionHandler(MessageSource messageSource, ExceptionMetrics exceptionMetrics,
                               ExceptionProperties properties, ExceptionAlertPublisher alertPublisher) {
        this.messageSource = messageSource;
        setExceptionMetrics(exceptionMetrics);
        setExceptionProperties(properties);
        setAlertPublisher(alertPublisher);
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
        recordMetrics(e);
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
     *
     * <p>优先级：MDC > Request Header（X-Trace-Id > X-Request-Id）
     */
    private String extractTraceId(HttpServletRequest request) {
        String traceId = TraceContext.getTraceId();
        if (traceId == null) {
            traceId = request.getHeader(TraceContext.HEADER_TRACE_ID);
        }
        if (traceId == null) {
            traceId = request.getHeader(HeaderConstants.X_REQUEST_ID);
        }
        return traceId;
    }
}
