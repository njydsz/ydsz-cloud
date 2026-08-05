package com.remisoft.common.exception.handler;

import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.MessageSource;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ServerWebExchange;

import com.remisoft.common.core.constant.HeaderConstants;
import com.remisoft.common.core.response.BaseResponse;
import com.remisoft.common.exception.code.UnifiedExceptionCode;
import com.remisoft.common.exception.config.ExceptionProperties;
import com.remisoft.common.exception.core.ExceptionInfo;
import com.remisoft.common.exception.custom.AbstractRemiException;
import com.remisoft.common.exception.custom.BusinessException;
import com.remisoft.common.exception.custom.SysException;
import com.remisoft.common.exception.metrics.ExceptionMetrics;

import lombok.extern.slf4j.Slf4j;

/**
 * Spring WebFlux 全局异常处理器
 *
 * <p>处理 WebFlux 应用中的各类异常，与 MVC 异常处理器对应。
 * 仅在 WebFlux 环境下自动装配。
 *
 * <p><b>职责：</b>
 * <ul>
 *   <li>处理业务异常、系统异常等</li>
 *   <li>统一返回 BaseResponse 或 ProblemDetail 格式（通过配置切换）</li>
 *   <li>记录异常指标（Counter + Timer）</li>
 *   <li>提取 traceId 用于链路追踪</li>
 * </ul>
 *
 * @author remi-team
 * @since 1.0.0
 * @see BaseExceptionHandler
 * @see MvcExceptionHandler
 */
@Slf4j
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
@ConditionalOnClass(name = "org.springframework.web.server.ServerWebExchange")
@ConditionalOnProperty(prefix = "remi.exception", name = "global-handler-enabled", havingValue = "true", matchIfMissing = true)
public class WebFluxExceptionHandler extends BaseExceptionHandler {

    private final MessageSource messageSource;

    /**
     * 构造 WebFlux 全局异常处理器
     *
     * @param messageSource    国际化消息源
     * @param exceptionMetrics  异常指标统计器（可选）
     * @param properties       异常模块配置属性（可选）
     */
    public WebFluxExceptionHandler(MessageSource messageSource,
                                   ExceptionMetrics exceptionMetrics,
                                   ExceptionProperties properties) {
        this.messageSource = messageSource;
        setExceptionMetrics(exceptionMetrics);
        setExceptionProperties(properties);
    }

    @Override
    protected String getLogPrefix() {
        return "【WebFlux】";
    }

    /**
     * 从 ServerWebExchange 提取 traceId
     *
     * <p>优先级：MDC > Request Header（X-Trace-Id > X-Request-Id）
     */
    private String extractTraceId(ServerWebExchange exchange) {
        String traceId = MDC.get(HeaderConstants.MDC_TRACE_ID_KEY);
        if (traceId == null && exchange != null) {
            traceId = exchange.getRequest().getHeaders().getFirst(HeaderConstants.TRACE_ID_HEADER);
            if (traceId == null) {
                traceId = exchange.getRequest().getHeaders().getFirst("X-Request-Id");
            }
        }
        return traceId;
    }

    // ============================ 异常处理方法 ============================

    /**
     * 处理业务异常（动态 HTTP 状态码）
     */
    @ExceptionHandler(BusinessException.class)
    public Object handleBusinessException(BusinessException e, ServerWebExchange exchange) {
        recordMetrics(e);
        log.warn("{}业务异常 | 路径: {} | 错误码: {} | 消息: {}",
                getLogPrefix(), exchange.getRequest().getPath().value(), e.getCode(), e.getMessage(), e);

        String traceId = extractTraceId(exchange);
        return buildResponse(e, exchange.getRequest().getPath().value(), traceId);
    }

    /**
     * 处理系统异常
     */
    @ExceptionHandler(SysException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Object handleSysException(SysException e, ServerWebExchange exchange) {
        recordMetrics(e);
        log.error("{}系统异常 | 路径: {} | 错误码: {} | 消息: {}",
                getLogPrefix(), exchange.getRequest().getPath().value(), e.getCode(), e.getMessage(), e);

        return buildResponse(e, exchange.getRequest().getPath().value(), extractTraceId(exchange));
    }

    /**
     * 处理其他 REMI 异常（兜底，捕获所有 AbstractRemiException 子类）
     */
    @ExceptionHandler(AbstractRemiException.class)
    public Object handleAbstractRemiException(AbstractRemiException e, ServerWebExchange exchange) {
        recordMetrics(e);
        log.warn("{}异常 | 路径: {} | 错误码: {} | 消息: {} | 类型: {}",
                getLogPrefix(), exchange.getRequest().getPath().value(), e.getCode(), e.getMessage(),
                e.getClass().getSimpleName(), e);

        return buildResponse(e, exchange.getRequest().getPath().value(), extractTraceId(exchange));
    }

    /**
     * 处理非法参数异常
     */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Object handleIllegalArgumentException(IllegalArgumentException e, ServerWebExchange exchange) {
        recordMetrics(e);
        log.error("{}非法参数异常 | 路径: {} | 消息: {}",
                getLogPrefix(), exchange.getRequest().getPath().value(), e.getMessage(), e);

        return buildResponse(e, exchange.getRequest().getPath().value(), extractTraceId(exchange));
    }

    /**
     * 处理非法状态异常
     *
     * <p>IllegalStateException 属于系统级异常（非业务异常），统一返回 SYSTEM_ERROR，
     * 避免暴露内部状态信息。
     */
    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Object handleIllegalStateException(IllegalStateException e, ServerWebExchange exchange) {
        recordMetrics(e);
        log.error("{}非法状态异常 | 路径: {} | 消息: {}",
                getLogPrefix(), exchange.getRequest().getPath().value(), e.getMessage(), e);

        ExceptionInfo info = buildExceptionInfo(e, exchange.getRequest().getPath().value(), extractTraceId(exchange));
        info.setCode(UnifiedExceptionCode.SYSTEM_ERROR.getCode());

        return BaseResponse.error(
                UnifiedExceptionCode.SYSTEM_ERROR.getCode(),
                info.getMessage(),
                includeExceptionInfo() ? info : null);
    }

    /**
     * 处理空指针异常
     */
    @ExceptionHandler(NullPointerException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Object handleNullPointerException(NullPointerException e, ServerWebExchange exchange) {
        recordMetrics(e);
        log.error("{}空指针异常 | 路径: {} | 消息: {}",
                getLogPrefix(), exchange.getRequest().getPath().value(), e.getMessage(), e);

        ExceptionInfo info = buildExceptionInfo(e, exchange.getRequest().getPath().value(), extractTraceId(exchange));
        info.setCode(UnifiedExceptionCode.SYSTEM_ERROR.getCode());

        return BaseResponse.error(
                UnifiedExceptionCode.SYSTEM_ERROR.getCode(),
                info.getMessage(),
                includeExceptionInfo() ? info : null);
    }

    /**
     * 处理所有未捕获的异常
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Object handleException(Exception e, ServerWebExchange exchange) {
        recordMetrics(e);
        log.error("{}系统异常 | 路径: {} | 类型: {} | 消息: {}",
                getLogPrefix(), exchange.getRequest().getPath().value(), e.getClass().getName(), e.getMessage(), e);

        ExceptionInfo info = buildExceptionInfo(e, exchange.getRequest().getPath().value(), extractTraceId(exchange));

        return BaseResponse.error(
                UnifiedExceptionCode.SYSTEM_ERROR.getCode(),
                info.getMessage(),
                includeExceptionInfo() ? info : null);
    }
}
