package com.njydsz.pmis.gateway.config;

import com.alibaba.fastjson2.JSON;
import com.njydsz.pmis.common.core.response.BaseResponse;
import com.njydsz.pmis.common.constant.CommonConstants;
import com.njydsz.pmis.common.util.TraceIdUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.HandlerStrategies;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.WebExceptionHandler;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * 网关全局异常处理器配置（P0-1）
 *
 * <p>注册自定义 {@link WebExceptionHandler} 作为全局异常处理器，
 * 替代 Spring Cloud Gateway 默认的 HTML 错误页，统一返回 {@link Result} JSON。
 *
 * <h3>覆盖场景</h3>
 * <ul>
 *   <li>404 — 路由匹配失败（NotFoundException）</li>
 *   <li>502 — 下游服务连接失败（ConnectException）</li>
 *   <li>504 — 下游服务响应超时（TimeoutException）</li>
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
@Configuration
public class GatewayErrorConfig {

    /**
     * 注册自定义网关异常处理器
     *
     * <p>通过 {@code @Order(-2)} 确保优先于默认的异常处理器。
     *
     * @return 网关异常处理器
     */
    @Bean
    @Order(-2)
    public WebExceptionHandler gatewayErrorHandler() {
        return new GatewayExceptionHandler();
    }

    /**
     * 网关全局异常处理器
     *
     * <p>实现 {@link WebExceptionHandler} 接口，
     * 拦截所有网关层异常并返回统一 {@link Result} JSON。
     */
    @Slf4j
    static class GatewayExceptionHandler implements WebExceptionHandler {

        @Override
        public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
            if (exchange.getResponse().isCommitted()) {
                return Mono.error(ex);
            }

            HttpStatus httpStatus = resolveHttpStatus(ex);
            int bizCode = resolveBizCode(httpStatus);
            String message = resolveMessage(ex, httpStatus);

            String traceId = exchange.getRequest().getHeaders().getFirst(CommonConstants.HEADER_TRACE_ID);
            if (traceId == null || traceId.isBlank()) {
                traceId = TraceIdUtil.generate();
            }

            BaseResponse<Void> body = BaseResponse.failed(bizCode, message);
            body.setTraceId(traceId);

            log.warn("[GatewayError] status={} bizCode={} traceId={} path={} error={}",
                    httpStatus.value(), bizCode, traceId, exchange.getRequest().getURI().getPath(),
                    ex.getClass().getSimpleName() + ": " + ex.getMessage());

            exchange.getResponse().setStatusCode(httpStatus);
            exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
            exchange.getResponse().getHeaders().add(CommonConstants.HEADER_TRACE_ID, traceId);

            byte[] bytes = JSON.toJSONString(body).getBytes(StandardCharsets.UTF_8);
            DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
            return exchange.getResponse().writeWith(Mono.just(buffer));
        }

        /**
         * 根据异常类型解析 HTTP 状态码
         */
        private HttpStatus resolveHttpStatus(Throwable ex) {
            if (ex instanceof ResponseStatusException rse) {
                return HttpStatus.resolve(rse.getStatusCode().value()) != null
                        ? HttpStatus.valueOf(rse.getStatusCode().value())
                        : HttpStatus.INTERNAL_SERVER_ERROR;
            }
            if (ex instanceof java.net.ConnectException) {
                return HttpStatus.BAD_GATEWAY;
            }
            if (ex instanceof java.util.concurrent.TimeoutException) {
                return HttpStatus.GATEWAY_TIMEOUT;
            }
            // NotFoundException 来自 spring-cloud-gateway
            String className = ex.getClass().getSimpleName();
            if ("NotFoundException".equals(className)) {
                return HttpStatus.NOT_FOUND;
            }
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }

        /**
         * 根据 HTTP 状态码映射业务错误码
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
         */
        private String resolveMessage(Throwable ex, HttpStatus httpStatus) {
            return switch (httpStatus) {
                case NOT_FOUND -> "error.NOT_FOUND";
                case BAD_GATEWAY -> "error.SERVICE_UNAVAILABLE";
                case SERVICE_UNAVAILABLE -> "error.SERVICE_UNAVAILABLE";
                case GATEWAY_TIMEOUT -> "error.GATEWAY_TIMEOUT";
                case REQUEST_TIMEOUT -> "error.REQUEST_TIMEOUT";
                case TOO_MANY_REQUESTS -> "error.RATE_LIMIT";
                case INTERNAL_SERVER_ERROR -> "error.INTERNAL_ERROR";
                default -> ex.getMessage() != null ? ex.getMessage() : "error.UNKNOWN";
            };
        }
    }
}
