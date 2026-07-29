package com.njydsz.common.exception.handler;

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

import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.exception.alert.ExceptionAlertPublisher;
import com.njydsz.common.exception.code.UnifiedExceptionCode;
import com.njydsz.common.exception.config.ExceptionProperties;
import com.njydsz.common.exception.core.ExceptionInfo;
import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.exception.custom.ConcurrencyException;
import com.njydsz.common.exception.custom.DuplicateException;
import com.njydsz.common.exception.custom.ExternalException;
import com.njydsz.common.exception.custom.InfrastructureException;
import com.njydsz.common.exception.custom.RateLimitException;
import com.njydsz.common.exception.custom.SysException;
import com.njydsz.common.exception.custom.ValidationException;
import com.njydsz.common.exception.custom.YdszSecurityException;
import com.njydsz.common.exception.custom.YdszTimeoutException;
import com.njydsz.common.exception.metrics.ExceptionMetrics;
import com.njydsz.common.exception.observability.TraceContext;

import lombok.extern.slf4j.Slf4j;

/**
 * Spring WebFlux 全局异常处理器
 *
 * <p>处理 WebFlux 应用中的各类异常，与 MVC 异常处理器对应。
 * 仅在 WebFlux 环境下自动装配。
 *
 * <p><b>职责：</b>
 * <ul>
 *   <li>处理业务异常、系统异常、安全异常等</li>
 *   <li>统一返回 BaseResponse 或 ProblemDetail 格式（通过配置切换）</li>
 *   <li>记录异常指标（Counter + Timer）</li>
 *   <li>提取 traceId 用于链路追踪</li>
 *   <li>生产环境堆栈脱敏</li>
 *   <li>异常告警发布（ERROR/FATAL 级别）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see BaseExceptionHandler
 * @see MvcExceptionHandler
 */
@Slf4j
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
@ConditionalOnClass(name = "org.springframework.web.server.ServerWebExchange")
@ConditionalOnProperty(prefix = "ydsz.exception", name = "global-handler-enabled", havingValue = "true", matchIfMissing = true)
public class WebFluxExceptionHandler extends BaseExceptionHandler {

    private final MessageSource messageSource;

    /**
     * 构造 WebFlux 全局异常处理器
     *
     * @param messageSource    国际化消息源
     * @param exceptionMetrics  异常指标统计器（可选）
     * @param properties       异常模块配置属性（可选）
     * @param alertPublisher   异常告警发布器（可选）
     */
    public WebFluxExceptionHandler(MessageSource messageSource,
                                   ExceptionMetrics exceptionMetrics,
                                   ExceptionProperties properties,
                                   ExceptionAlertPublisher alertPublisher) {
        this.messageSource = messageSource;
        setExceptionMetrics(exceptionMetrics);
        setExceptionProperties(properties);
        setAlertPublisher(alertPublisher);
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
        String traceId = TraceContext.getTraceId();
        if (traceId == null && exchange != null) {
            traceId = exchange.getRequest().getHeaders().getFirst(TraceContext.HEADER_TRACE_ID);
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
     * 处理并发冲突异常
     */
    @ExceptionHandler(ConcurrencyException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Object handleConcurrencyException(ConcurrencyException e, ServerWebExchange exchange) {
        recordMetrics(e);
        log.warn("{}并发冲突异常 | 路径: {} | 错误码: {} | 消息: {}",
                getLogPrefix(), exchange.getRequest().getPath().value(), e.getCode(), e.getMessage(), e);

        return buildResponse(e, exchange.getRequest().getPath().value(), extractTraceId(exchange));
    }

    /**
     * 处理重复提交异常
     */
    @ExceptionHandler(DuplicateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Object handleDuplicateException(DuplicateException e, ServerWebExchange exchange) {
        recordMetrics(e);
        log.warn("{}重复提交异常 | 路径: {} | 错误码: {} | 消息: {}",
                getLogPrefix(), exchange.getRequest().getPath().value(), e.getCode(), e.getMessage(), e);

        return buildResponse(e, exchange.getRequest().getPath().value(), extractTraceId(exchange));
    }

    /**
     * 处理限流异常
     */
    @ExceptionHandler(RateLimitException.class)
    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
    public Object handleRateLimitException(RateLimitException e, ServerWebExchange exchange) {
        recordMetrics(e);
        log.warn("{}限流异常 | 路径: {} | 错误码: {} | 消息: {}",
                getLogPrefix(), exchange.getRequest().getPath().value(), e.getCode(), e.getMessage(), e);

        return buildResponse(e, exchange.getRequest().getPath().value(), extractTraceId(exchange));
    }

    /**
     * 处理安全异常
     */
    @ExceptionHandler(YdszSecurityException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public Object handleSecurityException(YdszSecurityException e, ServerWebExchange exchange) {
        recordMetrics(e);
        log.warn("{}安全异常 | 路径: {} | 错误码: {} | 消息: {}",
                getLogPrefix(), exchange.getRequest().getPath().value(), e.getCode(), e.getMessage(), e);

        return buildResponse(e, exchange.getRequest().getPath().value(), extractTraceId(exchange));
    }

    /**
     * 处理校验异常
     */
    @ExceptionHandler(ValidationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Object handleValidationException(ValidationException e, ServerWebExchange exchange) {
        recordMetrics(e);
        log.warn("{}校验异常 | 路径: {} | 错误码: {} | 消息: {}",
                getLogPrefix(), exchange.getRequest().getPath().value(), e.getCode(), e.getMessage(), e);

        return buildResponse(e, exchange.getRequest().getPath().value(), extractTraceId(exchange));
    }

    /**
     * 处理超时异常
     */
    @ExceptionHandler(YdszTimeoutException.class)
    @ResponseStatus(HttpStatus.GATEWAY_TIMEOUT)
    public Object handleTimeoutException(YdszTimeoutException e, ServerWebExchange exchange) {
        recordMetrics(e);
        log.error("{}超时异常 | 路径: {} | 错误码: {} | 消息: {}",
                getLogPrefix(), exchange.getRequest().getPath().value(), e.getCode(), e.getMessage(), e);

        return buildResponse(e, exchange.getRequest().getPath().value(), extractTraceId(exchange));
    }

    /**
     * 处理外部服务异常
     */
    @ExceptionHandler(ExternalException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public Object handleExternalException(ExternalException e, ServerWebExchange exchange) {
        recordMetrics(e);
        log.error("{}外部服务异常 | 路径: {} | 错误码: {} | 消息: {}",
                getLogPrefix(), exchange.getRequest().getPath().value(), e.getCode(), e.getMessage(), e);

        return buildResponse(e, exchange.getRequest().getPath().value(), extractTraceId(exchange));
    }

    /**
     * 处理基础设施异常
     */
    @ExceptionHandler(InfrastructureException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Object handleInfrastructureException(InfrastructureException e, ServerWebExchange exchange) {
        recordMetrics(e);
        log.error("{}基础设施异常 | 路径: {} | 错误码: {} | 消息: {}",
                getLogPrefix(), exchange.getRequest().getPath().value(), e.getCode(), e.getMessage(), e);

        return buildResponse(e, exchange.getRequest().getPath().value(), extractTraceId(exchange));
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
