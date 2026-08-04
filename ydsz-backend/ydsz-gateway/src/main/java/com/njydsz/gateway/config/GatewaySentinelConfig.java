package com.njydsz.gateway.config;

import java.nio.charset.StandardCharsets;

import com.njydsz.common.json.YdszJson;

import jakarta.annotation.PostConstruct;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.ServerResponse;

import com.alibaba.csp.sentinel.adapter.gateway.sc.callback.BlockRequestHandler;
import com.alibaba.csp.sentinel.adapter.gateway.sc.callback.GatewayCallbackManager;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeException;
import com.alibaba.csp.sentinel.slots.block.flow.FlowException;
import com.alibaba.csp.sentinel.slots.system.SystemBlockException;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.core.trace.TraceIdGenerator;

import lombok.extern.slf4j.Slf4j;

/**
 * 网关 Sentinel 配置（P1-8 增强）
 *
 * <p>自定义网关层限流/熔断响应，统一返回 {@link Result} 格式 JSON。
 *
 * <h3>P1-8 增强：区分限流与熔断响应</h3>
 * <ul>
 *   <li>限流（FlowException）→ 429 + error.RATE_LIMIT</li>
 *   <li>熔断（DegradeException）→ 503 + error.SERVICE_DEGRADED</li>
 *   <li>系统自适应保护（SystemBlockException）→ 503 + error.SYSTEM_PROTECTED</li>
 *   <li>其他 Sentinel 阻断 → 429 + error.RATE_LIMIT</li>
 * </ul>
 *
 * <p>所有响应均注入 traceId，便于排障关联。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@Slf4j
@Configuration
public class GatewaySentinelConfig {

    private final GatewayMetrics gatewayMetrics;

    public GatewaySentinelConfig(GatewayMetrics gatewayMetrics) {
        this.gatewayMetrics = gatewayMetrics;
    }

    /**
     * 初始化网关限流/熔断响应处理器
     *
     * <p>P1-8: 根据异常类型区分限流与熔断，返回不同 HTTP 状态码与业务错误码。
     * P0-3 修复：熔断触发时上报 Prometheus 指标（CircuitBreakerState=open）。
     */
    @PostConstruct
    public void init() {
        BlockRequestHandler handler = (exchange, ex) -> {
            String traceId = TraceIdGenerator.generateTraceId();

            HttpStatus httpStatus;
            int bizCode;
            String message;

            if (ex instanceof DegradeException) {
                httpStatus = HttpStatus.SERVICE_UNAVAILABLE;
                bizCode = 50300;
                message = "error.SERVICE_DEGRADED";
                // P0-3 修复：熔断触发时上报指标（state=1 open）
                String routeId = extractRouteId(exchange);
                if (routeId != null) {
                    gatewayMetrics.setCircuitBreakerState(routeId, 1);
                }
            } else if (ex instanceof SystemBlockException) {
                httpStatus = HttpStatus.SERVICE_UNAVAILABLE;
                bizCode = 50301;
                message = "error.SYSTEM_PROTECTED";
            } else if (ex instanceof FlowException) {
                httpStatus = HttpStatus.TOO_MANY_REQUESTS;
                bizCode = 42900;
                message = "error.RATE_LIMIT";
            } else if (ex instanceof BlockException) {
                httpStatus = HttpStatus.TOO_MANY_REQUESTS;
                bizCode = 42900;
                message = "error.RATE_LIMIT";
            } else {
                httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;
                bizCode = 50000;
                message = "error.INTERNAL_ERROR";
            }

            BaseResponse<Void> body = BaseResponse.error(String.valueOf(bizCode), message);
            body.setTraceId(traceId);

            log.warn("[SentinelBlock] status={} bizCode={} traceId={} path={} ex={}",
                    httpStatus.value(), bizCode, traceId,
                    exchange.getRequest().getURI().getPath(),
                    ex.getClass().getSimpleName());

            return ServerResponse.status(httpStatus)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Content-Type", MediaType.APPLICATION_JSON_VALUE + ";charset=" + StandardCharsets.UTF_8)
                    .header(GatewayConstants.HEADER_TRACE_ID, traceId)
                    .bodyValue(YdszJson.toJson(body));
        };
        GatewayCallbackManager.setBlockHandler(handler);

        log.info("[SentinelConfig] 限流/熔断响应处理器初始化完成（P1-8: 区分限流(429)/熔断(503)/系统保护(503)；P0-3: 熔断指标上报）");
    }

    /**
     * 从交换上下文中提取路由 ID。
     *
     * @param exchange 服务器 Web 交换上下文
     * @return 路由 ID，无法获取时返回 null
     */
    private String extractRouteId(org.springframework.web.server.ServerWebExchange exchange) {
        try {
            Object route = exchange.getAttribute(
                    org.springframework.cloud.gateway.support.ServerWebExchangeUtils.GATEWAY_ROUTE_ATTR);
            if (route instanceof org.springframework.cloud.gateway.route.Route routeObj) {
                return routeObj.getId();
            }
        } catch (Exception e) {
            // 忽略异常，指标上报不应影响请求处理
        }
        return null;
    }
}
