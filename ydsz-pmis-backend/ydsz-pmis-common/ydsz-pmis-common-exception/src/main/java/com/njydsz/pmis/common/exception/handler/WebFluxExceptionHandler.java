package com.njydsz.pmis.common.exception.handler;

import com.njydsz.pmis.common.core.response.BaseResponse;
import com.njydsz.pmis.common.exception.custom.BusinessException;
import com.njydsz.pmis.common.exception.custom.ConcurrencyException;
import com.njydsz.pmis.common.exception.custom.DuplicateException;
import com.njydsz.pmis.common.exception.custom.ExternalException;
import com.njydsz.pmis.common.exception.custom.InfrastructureException;
import com.njydsz.pmis.common.exception.custom.RateLimitException;
import com.njydsz.pmis.common.exception.custom.YdszSecurityException;
import com.njydsz.pmis.common.exception.custom.YdszTimeoutException;
import com.njydsz.pmis.common.exception.custom.SysException;
import com.njydsz.pmis.common.exception.custom.ValidationException;
import com.njydsz.pmis.common.exception.code.UnifiedExceptionCode;
import com.njydsz.pmis.common.exception.core.ExceptionInfo;
import com.njydsz.pmis.common.exception.metrics.ExceptionMetrics;
import com.njydsz.pmis.common.exception.observability.TraceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ServerWebExchange;

/**
 * Spring WebFlux 全局异常处理器
 *
 * <p>处理 WebFlux 应用中的各类异常，与 MVC 异常处理器对应。
 * 仅在 WebFlux 环境下自动装配。
 *
 * <p><b>职责：</b>
 * <ul>
 *   <li>处理业务异常、系统异常、安全异常等</li>
 *   <li>统一返回 BaseResponse 格式</li>
 *   <li>记录异常指标</li>
 *   <li>提取 traceId 用于链路追踪</li>
 * </ul>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 3.0.0
 * @see MvcExceptionHandler
 */
@Slf4j
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
@ConditionalOnClass(name = "org.springframework.web.server.ServerWebExchange")
@ConditionalOnProperty(prefix = "ydsz.exception", name = "global-handler-enabled", havingValue = "true", matchIfMissing = true)
public class WebFluxExceptionHandler {

    private final MessageSource messageSource;
    private final ExceptionMetrics exceptionMetrics;

    public WebFluxExceptionHandler(MessageSource messageSource, ExceptionMetrics exceptionMetrics) {
        this.messageSource = messageSource;
        this.exceptionMetrics = exceptionMetrics;
    }

    /**
     * 记录异常指标
     */
    private void recordExceptionMetrics(Throwable throwable) {
        if (exceptionMetrics != null) {
            exceptionMetrics.recordException(throwable);
        }
    }

    /**
     * 从 ServerWebExchange 提取 traceId
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

    /**
     * 构建异常信息
     */
    private ExceptionInfo buildExceptionInfo(Exception e, String path, String traceId) {
        ExceptionInfo info = new ExceptionInfo();
        info.setPath(path);
        info.setTraceId(traceId);
        info.setMessage(e.getMessage());
        return info;
    }

    /**
     * 处理业务异常
     */
    @ExceptionHandler(BusinessException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public BaseResponse<?> handleBusinessException(BusinessException e, ServerWebExchange exchange) {
        recordExceptionMetrics(e);
        String path = exchange.getRequest().getPath().value();
        log.warn("【全局】业务异常 | 路径: {} | 错误码: {} | 消息: {}",
                path, e.getCode(), e.getMessage(), e);

        ExceptionInfo info = buildExceptionInfo(e, path, extractTraceId(exchange));
        return BaseResponse.error(e.getCode(), e.getMessage(), info);
    }

    /**
     * 处理并发冲突异常
     */
    @ExceptionHandler(ConcurrencyException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public BaseResponse<?> handleConcurrencyException(ConcurrencyException e, ServerWebExchange exchange) {
        recordExceptionMetrics(e);
        String path = exchange.getRequest().getPath().value();
        log.warn("【全局】并发冲突异常 | 路径: {} | 错误码: {} | 消息: {}",
                path, e.getCode(), e.getMessage(), e);

        ExceptionInfo info = buildExceptionInfo(e, path, extractTraceId(exchange));
        return BaseResponse.error(e.getCode(), e.getMessage(), info);
    }

    /**
     * 处理重复提交异常
     */
    @ExceptionHandler(DuplicateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public BaseResponse<?> handleDuplicateException(DuplicateException e, ServerWebExchange exchange) {
        recordExceptionMetrics(e);
        String path = exchange.getRequest().getPath().value();
        log.warn("【全局】重复提交异常 | 路径: {} | 错误码: {} | 消息: {}",
                path, e.getCode(), e.getMessage(), e);

        ExceptionInfo info = buildExceptionInfo(e, path, extractTraceId(exchange));
        return BaseResponse.error(e.getCode(), e.getMessage(), info);
    }

    /**
     * 处理限流异常
     */
    @ExceptionHandler(RateLimitException.class)
    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
    public BaseResponse<?> handleRateLimitException(RateLimitException e, ServerWebExchange exchange) {
        recordExceptionMetrics(e);
        String path = exchange.getRequest().getPath().value();
        log.warn("【全局】限流异常 | 路径: {} | 错误码: {} | 消息: {}",
                path, e.getCode(), e.getMessage(), e);

        ExceptionInfo info = buildExceptionInfo(e, path, extractTraceId(exchange));
        return BaseResponse.error(e.getCode(), e.getMessage(), info);
    }

    /**
     * 处理安全异常
     */
    @ExceptionHandler(YdszSecurityException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public BaseResponse<?> handleSecurityException(YdszSecurityException e, ServerWebExchange exchange) {
        recordExceptionMetrics(e);
        String path = exchange.getRequest().getPath().value();
        log.warn("【全局】安全异常 | 路径: {} | 错误码: {} | 消息: {}",
                path, e.getCode(), e.getMessage(), e);

        ExceptionInfo info = buildExceptionInfo(e, path, extractTraceId(exchange));
        return BaseResponse.error(e.getCode(), e.getMessage(), info);
    }

    /**
     * 处理校验异常
     */
    @ExceptionHandler(ValidationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public BaseResponse<?> handleValidationException(ValidationException e, ServerWebExchange exchange) {
        recordExceptionMetrics(e);
        String path = exchange.getRequest().getPath().value();
        log.warn("【全局】校验异常 | 路径: {} | 错误码: {} | 消息: {}",
                path, e.getCode(), e.getMessage(), e);

        ExceptionInfo info = buildExceptionInfo(e, path, extractTraceId(exchange));
        return BaseResponse.error(e.getCode(), e.getMessage(), info);
    }

    /**
     * 处理超时异常
     */
    @ExceptionHandler(YdszTimeoutException.class)
    @ResponseStatus(HttpStatus.GATEWAY_TIMEOUT)
    public BaseResponse<?> handleTimeoutException(YdszTimeoutException e, ServerWebExchange exchange) {
        recordExceptionMetrics(e);
        String path = exchange.getRequest().getPath().value();
        log.error("【全局】超时异常 | 路径: {} | 错误码: {} | 消息: {}",
                path, e.getCode(), e.getMessage(), e);

        ExceptionInfo info = buildExceptionInfo(e, path, extractTraceId(exchange));
        return BaseResponse.error(e.getCode(), e.getMessage(), info);
    }

    /**
     * 处理外部服务异常
     */
    @ExceptionHandler(ExternalException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public BaseResponse<?> handleExternalException(ExternalException e, ServerWebExchange exchange) {
        recordExceptionMetrics(e);
        String path = exchange.getRequest().getPath().value();
        log.error("【全局】外部服务异常 | 路径: {} | 错误码: {} | 消息: {}",
                path, e.getCode(), e.getMessage(), e);

        ExceptionInfo info = buildExceptionInfo(e, path, extractTraceId(exchange));
        return BaseResponse.error(e.getCode(), e.getMessage(), info);
    }

    /**
     * 处理基础设施异常
     */
    @ExceptionHandler(InfrastructureException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public BaseResponse<?> handleInfrastructureException(InfrastructureException e, ServerWebExchange exchange) {
        recordExceptionMetrics(e);
        String path = exchange.getRequest().getPath().value();
        log.error("【全局】基础设施异常 | 路径: {} | 错误码: {} | 消息: {}",
                path, e.getCode(), e.getMessage(), e);

        ExceptionInfo info = buildExceptionInfo(e, path, extractTraceId(exchange));
        return BaseResponse.error(e.getCode(), e.getMessage(), info);
    }

    /**
     * 处理系统异常
     */
    @ExceptionHandler(SysException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public BaseResponse<?> handleSysException(SysException e, ServerWebExchange exchange) {
        recordExceptionMetrics(e);
        String path = exchange.getRequest().getPath().value();
        log.error("【全局】系统异常 | 路径: {} | 错误码: {} | 消息: {}",
                path, e.getCode(), e.getMessage(), e);

        ExceptionInfo info = buildExceptionInfo(e, path, extractTraceId(exchange));
        return BaseResponse.error(e.getCode(), e.getMessage(), info);
    }

    /**
     * 处理非法参数异常
     */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public BaseResponse<?> handleIllegalArgumentException(IllegalArgumentException e, ServerWebExchange exchange) {
        recordExceptionMetrics(e);
        String path = exchange.getRequest().getPath().value();
        log.error("【全局】非法参数异常 | 路径: {} | 消息: {}", path, e.getMessage(), e);

        ExceptionInfo info = buildExceptionInfo(e, path, extractTraceId(exchange));
        info.setCode(UnifiedExceptionCode.ILLEGAL_ARGUMENT.getCode());
        return BaseResponse.error(UnifiedExceptionCode.ILLEGAL_ARGUMENT.getCode(), e.getMessage(), info);
    }

    /**
     * 处理非法状态异常
     */
    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public BaseResponse<?> handleIllegalStateException(IllegalStateException e, ServerWebExchange exchange) {
        recordExceptionMetrics(e);
        String path = exchange.getRequest().getPath().value();
        log.error("【全局】非法状态异常 | 路径: {} | 消息: {}", path, e.getMessage(), e);

        ExceptionInfo info = buildExceptionInfo(e, path, extractTraceId(exchange));
        info.setCode(UnifiedExceptionCode.INVALID_BUSINESS_STATE.getCode());
        return BaseResponse.error(UnifiedExceptionCode.INVALID_BUSINESS_STATE.getCode(), e.getMessage(), info);
    }

    /**
     * 处理空指针异常
     */
    @ExceptionHandler(NullPointerException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public BaseResponse<?> handleNullPointerException(NullPointerException e, ServerWebExchange exchange) {
        recordExceptionMetrics(e);
        String path = exchange.getRequest().getPath().value();
        log.error("【全局】空指针异常 | 路径: {} | 消息: {}", path, e.getMessage(), e);

        String message = messageSource.getMessage("system.error", null,
                "系统异常，请联系管理员", LocaleContextHolder.getLocale());

        ExceptionInfo info = buildExceptionInfo(e, path, extractTraceId(exchange));
        info.setCode(UnifiedExceptionCode.SYSTEM_ERROR.getCode());
        info.setMessage(message);

        return BaseResponse.error(UnifiedExceptionCode.SYSTEM_ERROR.getCode(), message, info);
    }

    /**
     * 处理所有未捕获的异常
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public BaseResponse<?> handleException(Exception e, ServerWebExchange exchange) {
        recordExceptionMetrics(e);
        String path = exchange.getRequest().getPath().value();
        log.error("【全局】系统异常 | 路径: {} | 类型: {} | 消息: {}",
                path, e.getClass().getName(), e.getMessage(), e);

        String message = messageSource.getMessage("system.error", null,
                "系统异常，请联系管理员", LocaleContextHolder.getLocale());

        ExceptionInfo info = buildExceptionInfo(e, path, extractTraceId(exchange));

        return BaseResponse.error(UnifiedExceptionCode.SYSTEM_ERROR.getCode(), message, info);
    }
}
