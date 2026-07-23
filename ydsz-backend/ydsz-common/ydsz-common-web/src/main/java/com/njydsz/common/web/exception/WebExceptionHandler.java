package com.njydsz.common.web.exception;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.njydsz.common.exception.alert.ExceptionAlertPublisher;
import com.njydsz.common.exception.config.ExceptionProperties;
import com.njydsz.common.exception.handler.BaseExceptionHandler;
import com.njydsz.common.exception.metrics.ExceptionMetrics;

import lombok.extern.slf4j.Slf4j;

/**
 * Web 层统一异常处理器
 *
 * <p>继承 {@link BaseExceptionHandler}，提供 Web 端的异常处理逻辑。
 * 核心职责：将各类异常统一映射为 {@link com.njydsz.common.core.response.BaseResponse} 标准响应格式，
 * 屏蔽内部实现细节，向上游返回友好的错误信息。
 *
 * <p><b>设计说明：</b>
 * <ul>
 *   <li>统一返回 200 HTTP 状态码，业务错误码在响应体中标识</li>
 *   <li>日志记录采用不同级别：error 用于业务异常，warn 用于参数校验异常</li>
 *   <li>参数异常合并多条错误信息为逗号分隔字符串</li>
 * </ul>
 *
 * <p><b>装配：</b>由 {@link WebExceptionAutoConfiguration} 通过 {@code @Bean} 方法创建，
 * 注入 {@link ExceptionMetrics}、{@link ExceptionProperties}、{@link ExceptionAlertPublisher} 等可选依赖。
 * {@code @RestControllerAdvice} 会被 Spring MVC 自动发现为控制器增强。
 *
 * <p><b>执行顺序：</b>{@link Ordered#HIGHEST_PRECEDENCE} + 20，
 * 在 {@code GlobalResponseAdvice} 之后、参数校验 Advice 之前执行。
 *
 * @author ydsz-team
 * @see BaseExceptionHandler
 * @see WebExceptionAutoConfiguration
 */
@Slf4j
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class WebExceptionHandler extends BaseExceptionHandler {

    public WebExceptionHandler(ExceptionMetrics exceptionMetrics,
                                ExceptionProperties properties,
                                ExceptionAlertPublisher alertPublisher) {
        setExceptionMetrics(exceptionMetrics);
        setExceptionProperties(properties);
        setAlertPublisher(alertPublisher);
    }

    @Override
    protected String getLogPrefix() {
        return "【Web端】";
    }
}
