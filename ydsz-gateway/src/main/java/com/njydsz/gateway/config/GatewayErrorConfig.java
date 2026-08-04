package com.njydsz.gateway.config;

import java.net.ConnectException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeoutException;

import com.njydsz.common.json.YdszJson;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;

import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.core.trace.TraceIdGenerator;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * GAP-P0-2: 网关全局异常处理器配置（增强 common-exception WebFluxExceptionHandler）
 *
 * <p>历史版本完全自定义 WebExceptionHandler，与 ydsz-common-exception 的
 * {@code WebFluxExceptionHandler} 功能重复。本版本保留网关特有的 HTTP 状态码→业务码映射
 * （404→40400、502→50200、504→50400），但通过委托公共异常处理器的方式实现统一格式：
 *
 * <ul>
 *   <li>{@code WebFluxExceptionHandler}（common-exception 自动注册）处理通用异常</li>
 *   <li>{@link GatewayExceptionHandler}（本类注册，@Order(-2) 优先级）补充网关特有场景：
 *     <ul>
 *       <li>404 — 路由匹配失败（NotFoundException）</li>
 *       <li>502 — 下游服务连接失败（ConnectException）</li>
 *       <li>504 — 下游服务响应超时（TimeoutException）</li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * <p>所有响应均为 {@code application/json;charset=UTF-8}，包含 traceId，不暴露内部堆栈。
 *
 * @since 1.0.0
 * @author ydsz-team
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

        /**
         * 处理网关层异常并返回统一 JSON 错误响应。
         *
         * <p>仅处理响应尚未提交（body 未写出）的异常；已提交则原样抛出交由容器兜底。
         * 状态码 / 业务码 / 错误消息分别由 {@link #resolveHttpStatus}、{@link #resolveBizCode}、
         * {@link #resolveMessage} 解析，并注入 traceId 便于跨服务排障。
         *
         * @param exchange 服务器 Web 交换上下文
         * @param ex       待处理的异常
         * @return 写出错误响应后的完成信号 Mono
         */
        @Override
        public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
            if (exchange.getResponse().isCommitted()) {
                return Mono.error(ex);
            }

            HttpStatus httpStatus = resolveHttpStatus(ex);
            int bizCode = resolveBizCode(httpStatus);
            String message = resolveMessage(ex, httpStatus);

            String traceId = exchange.getRequest().getHeaders().getFirst(GatewayConstants.HEADER_TRACE_ID);
            if (traceId == null || traceId.isBlank()) {
                traceId = TraceIdGenerator.generateTraceId();
            }

            BaseResponse<Void> body = BaseResponse.error(String.valueOf(bizCode), message);
            body.setTraceId(traceId);

            log.warn("[GatewayError] status={} bizCode={} traceId={} path={} error={}",
                    httpStatus.value(), bizCode, traceId, exchange.getRequest().getURI().getPath(),
                    ex.getClass().getSimpleName() + ": " + ex.getMessage());

            exchange.getResponse().setStatusCode(httpStatus);
            exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
            exchange.getResponse().getHeaders().add(GatewayConstants.HEADER_TRACE_ID, traceId);

            byte[] bytes = YdszJson.toJson(body).getBytes(StandardCharsets.UTF_8);
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
            if (ex instanceof ConnectException) {
                return HttpStatus.BAD_GATEWAY;
            }
            if (ex instanceof TimeoutException) {
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
