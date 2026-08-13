package com.njydsz.common.exception.handler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.MessageSource;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ServerWebExchange;

import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.exception.code.CoreExceptionCode;
import com.njydsz.common.exception.config.ExceptionProperties;
import com.njydsz.common.exception.core.ExceptionInfo;
import com.njydsz.common.exception.custom.AbstractYdszException;
import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.exception.custom.SysException;
import com.njydsz.common.exception.metrics.ExceptionMetrics;

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

    /**
     * 系统错误兜底文案（i18n key 解析失败时使用，避免向客户端泄露内部异常细节）
     */
    private static final String DEFAULT_SYSTEM_ERROR_MESSAGE = "系统异常，请联系管理员";

    /**
     * 非法参数兜底文案（i18n key 解析失败时使用，避免泄露内部异常细节）
     */
    private static final String DEFAULT_ILLEGAL_ARGUMENT_MESSAGE = "非法参数";

    private final MessageSource messageSource;

    /**
     * 构造 WebFlux 全局异常处理器
     *
     * @param messageSource    国际化消息源
     * @param exceptionMetrics  异常指标统计器（可选）
     * @param properties       异常模块配置属性（可选）
     * @param environment Spring 环境对象
     */
    public WebFluxExceptionHandler(Environment environment,
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
        return "【WebFlux】";
    }

    // ============================ 异常处理方法 ============================

    /**
     * 处理业务异常（动态 HTTP 状态码）
     * @param e 异常对象
     * @param exchange WebFlux 请求上下文
     * @return 处理结果
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
     * @param e 异常对象
     * @param exchange WebFlux 请求上下文
     * @return 处理结果
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
     * 处理其他 YDSZ 异常（兜底，捕获所有 AbstractYdszException 子类）
     * @param e 异常对象
     * @param exchange WebFlux 请求上下文
     * @return 处理结果
     */
    @ExceptionHandler(AbstractYdszException.class)
    public Object handleAbstractYdszException(AbstractYdszException e, ServerWebExchange exchange) {
        recordMetrics(e);
        log.warn("{}异常 | 路径: {} | 错误码: {} | 消息: {} | 类型: {}",
                getLogPrefix(), exchange.getRequest().getPath().value(), e.getCode(), e.getMessage(),
                e.getClass().getSimpleName(), e);

        return buildResponse(e, exchange.getRequest().getPath().value(), extractTraceId(exchange));
    }

    /**
     * 处理非法参数异常
     * @param e 异常对象
     * @param exchange WebFlux 请求上下文
     * @return 处理结果
     */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Object handleIllegalArgumentException(IllegalArgumentException e, ServerWebExchange exchange) {
        recordMetrics(e);
        log.error("{}非法参数异常 | 路径: {} | 消息: {}",
                getLogPrefix(), exchange.getRequest().getPath().value(), e.getMessage(), e);

        // 与 MVC 处理器行为对齐：按请求 Locale 解析 i18n 文案，原始异常消息仅保留在日志中
        String message = resolveMessage(
                CoreExceptionCode.ILLEGAL_ARGUMENT.getKey(), null,
                DEFAULT_ILLEGAL_ARGUMENT_MESSAGE);
        return buildStandardErrorResponse(
                CoreExceptionCode.ILLEGAL_ARGUMENT.getCode(),
                CoreExceptionCode.ILLEGAL_ARGUMENT.getKey(),
                message,
                HttpStatus.BAD_REQUEST.value(),
                exchange.getRequest().getPath().value()
        );
    }

    /**
     * 处理非法状态异常
     *
     * <p>IllegalStateException 属于系统级异常（非业务异常），统一返回 SYSTEM_ERROR，
     * 避免暴露内部状态信息。
     * @param e 异常对象
     * @param exchange WebFlux 请求上下文
     * @return 处理结果
     */
    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Object handleIllegalStateException(IllegalStateException e, ServerWebExchange exchange) {
        recordMetrics(e);
        log.error("{}非法状态异常 | 路径: {} | 消息: {}",
                getLogPrefix(), exchange.getRequest().getPath().value(), e.getMessage(), e);

        return buildSystemErrorResponse(e, exchange);
    }

    /**
     * 处理空指针异常
     * @param e 异常对象
     * @param exchange WebFlux 请求上下文
     * @return 处理结果
     */
    @ExceptionHandler(NullPointerException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Object handleNullPointerException(NullPointerException e, ServerWebExchange exchange) {
        recordMetrics(e);
        log.error("{}空指针异常 | 路径: {} | 消息: {}",
                getLogPrefix(), exchange.getRequest().getPath().value(), e.getMessage(), e);

        return buildSystemErrorResponse(e, exchange);
    }

    /**
     * 处理所有未捕获的异常
     * @param e 异常对象
     * @param exchange WebFlux 请求上下文
     * @return 处理结果
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public Object handleException(Exception e, ServerWebExchange exchange) {
        recordMetrics(e);
        log.error("{}系统异常 | 路径: {} | 类型: {} | 消息: {}",
                getLogPrefix(), exchange.getRequest().getPath().value(), e.getClass().getName(), e.getMessage(), e);

        return buildSystemErrorResponse(e, exchange);
    }

    /**
     * 构建系统级错误响应（掩码内部异常细节）。
     *
     * <p>与 MVC 处理器行为对齐：仅返回 i18n 的 system.error 文案，
     * 原始异常消息仅保留在日志与堆栈详情（dev/test）中，避免生产环境信息泄露。
     *
     * @param e        原始异常
     * @param exchange WebFlux 请求上下文
     * @return 统一错误响应
     */
    private Object buildSystemErrorResponse(Throwable e, ServerWebExchange exchange) {
        String message = resolveMessage(
                CoreExceptionCode.SYSTEM_ERROR.getKey(), null,
                DEFAULT_SYSTEM_ERROR_MESSAGE);
        ExceptionInfo info = buildExceptionInfo(e, exchange.getRequest().getPath().value(), extractTraceId(exchange));
        info.setCode(CoreExceptionCode.SYSTEM_ERROR.getCode());
        info.setMessage(message);
        return errorResponse(
                CoreExceptionCode.SYSTEM_ERROR.getCode(),
                message,
                includeExceptionInfo() ? info : null);
    }
}
