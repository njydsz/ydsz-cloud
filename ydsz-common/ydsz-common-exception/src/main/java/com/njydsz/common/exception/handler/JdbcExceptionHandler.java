package com.njydsz.common.exception.handler;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.njydsz.common.core.constant.HeaderConstants;
import com.njydsz.common.core.context.RequestContext;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.exception.code.UnifiedExceptionCode;
import com.njydsz.common.exception.config.ExceptionProperties;
import com.njydsz.common.exception.core.ExceptionInfo;
import com.njydsz.common.exception.metrics.ExceptionMetrics;

import org.springframework.core.env.Environment;
import lombok.extern.slf4j.Slf4j;

/**
 * JDBC 数据访问异常处理器
 *
 * <p>仅在 spring-jdbc 存在时注册，处理 DataAccessException 及其子类异常。
 *
 * <p><b>修复说明：</b>添加 {@link RestControllerAdvice} 注解使 {@code @ExceptionHandler}
 * 方法能够正确拦截 Controller 层抛出的数据访问异常。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see BaseExceptionHandler
 */
@Slf4j
@RestControllerAdvice
@ConditionalOnClass(name = "org.springframework.dao.DataAccessException")
public class JdbcExceptionHandler extends BaseExceptionHandler {

    /**
     * 构造 JDBC 异常处理器
     *
     * @param environment       Spring 环境对象
     * @param exceptionMetrics  异常指标统计器
     * @param properties       异常模块配置属性（可为 null）
     */
    public JdbcExceptionHandler(Environment environment,
                               ExceptionMetrics exceptionMetrics,
                               ExceptionProperties properties) {
        super(environment);
        setExceptionMetrics(environment, exceptionMetrics);
        setExceptionProperties(environment, properties);
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

        // 直接使用 DATABASE_ERROR key 作为客户端返回消息，
        // 实际 i18n 文案按 key 从 messages*.properties 加载，保持一致
        String message = UnifiedExceptionCode.DATABASE_ERROR.getKey();
        ExceptionInfo info = buildExceptionInfo(e, request.getRequestURI(), extractTraceId(request));
        info.setCode(UnifiedExceptionCode.DATABASE_ERROR.getCode());
        info.setMessage(message);

        return errorResponse(
                UnifiedExceptionCode.DATABASE_ERROR.getCode(),
                message,
                includeExceptionInfo() ? info : null
        );
    }

    /**
     * 从 RequestContext / MDC / Request Header 提取 traceId
     *
     * <p>优先级：RequestContext > MDC > Request Header（X-Trace-Id > X-Request-Id）
     */
    private String extractTraceId(HttpServletRequest request) {
        String traceId = RequestContext.getTraceId();
        if (traceId == null || traceId.isBlank()) {
            traceId = MDC.get("traceId");
        }
        if (traceId == null && request != null) {
            traceId = request.getHeader(HeaderConstants.TRACE_ID_HEADER);
        }
        if (traceId == null && request != null) {
            traceId = request.getHeader("X-Request-Id");
        }
        return traceId;
    }
}
