package com.njydsz.pmis.gateway.config;

import com.alibaba.fastjson2.JSON;
import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.common.constant.CommonConstants;
import com.njydsz.pmis.common.util.TraceIdUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.web.ServerProperties;
import org.springframework.boot.autoconfigure.web.WebProperties;
import org.springframework.boot.autoconfigure.web.reactive.error.AbstractErrorWebExceptionHandler;
import org.springframework.boot.web.reactive.error.ErrorAttributes;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.RequestPredicates;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 网关全局异常处理器（P0-1）
 *
 * <p>统一处理网关层所有异常，返回 {@link Result} JSON 格式，
 * 替代 Spring Cloud Gateway 默认的 HTML/纯文本错误页。
 *
 * <h3>覆盖场景</h3>
 * <ul>
 *   <li>404 NoHandlerFoundException — 路由匹配失败</li>
 *   <li>502/503/504 — 下游服务不可用、连接超时、响应超时</li>
 *   <li>500 — 网关内部异常</li>
 *   <li>ResponseStatusException — 携带 HTTP 状态码的业务异常</li>
 * </ul>
 *
 * <h3>设计原则</h3>
 * <ol>
 *   <li>所有响应均为 {@code application/json;charset=UTF-8}</li>
 *   <li>所有响应包含 {@code traceId}，与网关日志关联</li>
 *   <li>不暴露内部堆栈信息，仅返回用户友好的错误码与消息</li>
 * </ol>
 *
 * @author ydsz-pmis-team
 * @since 2.2.0
 */
@Slf4j
@Order(-2)
public class GatewayExceptionHandler extends AbstractErrorWebExceptionHandler {

    /**
     * 构造网关全局异常处理器
     *
     * @param errorAttributes    错误属性
     * @param resourceProperties 资源属性
     * @param serverProperties   服务器属性
     * @param applicationContext Spring 上下文
     * @param configurer         编解码器配置
     */
    public GatewayExceptionHandler(ErrorAttributes errorAttributes,
                                   WebProperties.Resources resourceProperties,
                                   ServerProperties serverProperties,
                                   ApplicationContext applicationContext,
                                   ServerCodecConfigurer configurer) {
        super(errorAttributes, resourceProperties, serverProperties, applicationContext);
        this.setMessageWriters(configurer.getWriters());
        this.setMessageReaders(configurer.getReaders());
    }

    /**
     * 注册异常路由，所有错误请求均由 {@link #renderErrorResponse} 处理
     *
     * @param errorAttributes 错误属性
     * @return 路由函数
     */
    @Override
    protected RouterFunction<ServerResponse> getRoutingFunction(ErrorAttributes errorAttributes) {
        return RouterFunctions.route(RequestPredicates.all(), this::renderErrorResponse);
    }

    /**
     * 渲染统一 JSON 错误响应
     *
     * <p>根据异常类型映射 HTTP 状态码与业务错误码，
     * 所有响应体统一为 {@link Result} JSON。
     *
     * @param request 服务器请求
     * @return 服务器响应 Mono
     */
    private Mono<ServerResponse> renderErrorResponse(ServerRequest request) {
        Throwable error = getError(request);
        Map<String, Object> errorAttrs = getErrorAttributes(request,
                org.springframework.boot.web.error.ErrorAttributeOptions.defaults());

        HttpStatus httpStatus = resolveHttpStatus(error, errorAttrs);
        int bizCode = resolveBizCode(httpStatus);
        String message = resolveMessage(error, httpStatus);

        String traceId = resolveTraceId(request);

        Result<Void> body = Result.failed(bizCode, message);
        body.setTraceId(traceId);

        log.warn("[GatewayError] status={} bizCode={} traceId={} path={} error={}",
                httpStatus.value(), bizCode, traceId, request.path(),
                error.getClass().getSimpleName() + ": " + error.getMessage());

        return ServerResponse.status(httpStatus)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE + ";charset=" + StandardCharsets.UTF_8)
                .header(CommonConstants.HEADER_TRACE_ID, traceId)
                .body(BodyInserters.fromValue(JSON.toJSONString(body)));
    }

    /**
     * 根据异常类型解析 HTTP 状态码
     *
     * @param error      异常
     * @param errorAttrs 错误属性
     * @return HTTP 状态码
     */
    private HttpStatus resolveHttpStatus(Throwable error, Map<String, Object> errorAttrs) {
        if (error instanceof ResponseStatusException rse) {
            return HttpStatus.resolve(rse.getStatusCode().value()) != null
                    ? HttpStatus.valueOf(rse.getStatusCode().value())
                    : HttpStatus.INTERNAL_SERVER_ERROR;
        }

        Object status = errorAttrs.get("status");
        if (status instanceof Integer s) {
            HttpStatus resolved = HttpStatus.resolve(s);
            if (resolved != null) {
                return resolved;
            }
        }

        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    /**
     * 根据 HTTP 状态码映射业务错误码
     *
     * @param httpStatus HTTP 状态码
     * @return 业务错误码
     */
    private int resolveBizCode(HttpStatus httpStatus) {
        return switch (httpStatus) {
            case NOT_FOUND -> 40400;
            case BAD_GATEWAY -> 50200;
            case SERVICE_UNAVAILABLE -> 50300;
            case GATEWAY_TIMEOUT -> 50400;
            case REQUEST_TIMEOUT -> 40800;
            case TOO_MANY_REQUESTS -> 42900;
            default -> httpStatus.value() * 100;
        };
    }

    /**
     * 解析用户友好的错误消息
     *
     * @param error      异常
     * @param httpStatus HTTP 状态码
     * @return 错误消息
     */
    private String resolveMessage(Throwable error, HttpStatus httpStatus) {
        return switch (httpStatus) {
            case NOT_FOUND -> "error.NOT_FOUND";
            case BAD_GATEWAY -> "error.SERVICE_UNAVAILABLE";
            case SERVICE_UNAVAILABLE -> "error.SERVICE_UNAVAILABLE";
            case GATEWAY_TIMEOUT -> "error.GATEWAY_TIMEOUT";
            case REQUEST_TIMEOUT -> "error.REQUEST_TIMEOUT";
            case TOO_MANY_REQUESTS -> "error.RATE_LIMIT";
            case INTERNAL_SERVER_ERROR -> "error.INTERNAL_ERROR";
            default -> error.getMessage() != null ? error.getMessage() : "error.UNKNOWN";
        };
    }

    /**
     * 解析 traceId（优先从请求头获取，否则生成新的）
     *
     * @param request 服务器请求
     * @return traceId
     */
    private String resolveTraceId(ServerRequest request) {
        String traceId = request.headers().firstHeader(CommonConstants.HEADER_TRACE_ID);
        if (traceId != null && !traceId.isBlank()) {
            return traceId;
        }
        return TraceIdUtil.generate();
    }
}
