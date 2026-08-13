package com.njydsz.common.exception.handler;

import jakarta.servlet.http.HttpServletRequest;

import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.exception.code.CoreExceptionCode;
import com.njydsz.common.exception.config.ExceptionProperties;
import com.njydsz.common.exception.core.ExceptionInfo;
import com.njydsz.common.exception.metrics.ExceptionMetrics;

import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataAccessException;
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

        // 通过 MessageSource 按当前请求 Locale 解析 i18n 文案，避免向客户端暴露裸 key
        String message = messageSource.getMessage(
                CoreExceptionCode.DATABASE_ERROR.getKey(), null,
                DEFAULT_DATABASE_ERROR_MESSAGE, LocaleContextHolder.getLocale());
        ExceptionInfo info = buildExceptionInfo(e, request.getRequestURI(), extractTraceId(request));
        info.setCode(CoreExceptionCode.DATABASE_ERROR.getCode());
        info.setMessage(message);

        return errorResponse(
                CoreExceptionCode.DATABASE_ERROR.getCode(),
                message,
                includeExceptionInfo() ? info : null
        );
    }
}
