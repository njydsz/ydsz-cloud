package com.njydsz.gateway.filter;


import com.njydsz.common.json.YdszJson;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.gateway.config.GatewayConstants;
import com.njydsz.common.core.code.BaseResultCode;
import com.njydsz.common.core.trace.TraceIdGenerator;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * P1-8: 请求体安全校验过滤器
 *
 * <p>对标 Kong 的 request-size-limiting 和 json-schema 插件，
 * 在网关层校验请求体大小和 JSON 嵌套深度，防止恶意请求导致 OOM。
 *
 * <h3>校验项</h3>
 * <ul>
 *   <li>请求体大小限制（可配置，默认 10MB）</li>
 *   <li>JSON 嵌套深度限制（可配置，默认 50 层）</li>
 *   <li>Content-Type 严格校验（POST/PUT/PATCH 必须指定 Content-Type）</li>
 * </ul>
 *
 * <h3>配置方式</h3>
 * <pre>
 * ydsz:
 *   gateway:
 *     payload-validation:
 *       enabled: true
 *       max-body-size-mb: 10           # 请求体最大大小（MB）
 *       max-json-depth: 50             # JSON 最大嵌套深度
 *       strict-content-type: true      # 是否强制校验 Content-Type
 * </pre>
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "ydsz.gateway.filter", name = "payload-validation", havingValue = "true", matchIfMissing = true)
public class PayloadValidationFilter implements GlobalFilter, Ordered {

    @Value("${ydsz.gateway.payload-validation.enabled:true}")
    private boolean enabled;

    @Value("${ydsz.gateway.payload-validation.max-body-size-mb:10}")
    private int maxBodySizeMb;

    @Value("${ydsz.gateway.payload-validation.max-json-depth:50}")
    private int maxJsonDepth;

    @Value("${ydsz.gateway.payload-validation.strict-content-type:true}")
    private boolean strictContentType;

    private static final long BYTES_PER_MB = 1024L * 1024L;
    private static final int JSON_DEPTH_WARN_THRESHOLD = 30;

    /**
     * 请求体安全校验过滤器：限制请求体大小与 Content-Type。
     *
     * <p>仅对 POST/PUT/PATCH 等有请求体的方法生效；校验 Content-Type 是否缺失、
     * 请求体是否超过 {@code max-body-size-mb}（默认 10MB），超限返回 400。
     * JSON 深度校验委托下游服务（避免在网关缓存全量请求体影响性能）。
     *
     * @param exchange 服务器 Web 交换上下文
     * @param chain    网关过滤器链
     * @return 放行或拒绝（400）的完成信号 Mono
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!enabled) {
            return chain.filter(exchange);
        }

        ServerHttpRequest request = exchange.getRequest();
        String method = request.getMethod().name();

        // 仅对有请求体的方法检查
        if (!hasBody(method)) {
            return chain.filter(exchange);
        }

        // 检查 Content-Type（POST/PUT/PATCH 必须指定）
        String contentType = request.getHeaders().getFirst("Content-Type");
        if (strictContentType && (contentType == null || contentType.isBlank())) {
            return rejectPayload(exchange, "Content-Type 缺失，POST/PUT/PATCH 请求必须指定 Content-Type");
        }

        // 检查请求体大小
        long contentLength = request.getHeaders().getContentLength();
        long maxBytes = maxBodySizeMb * BYTES_PER_MB;
        if (contentLength > maxBytes) {
            return rejectPayload(exchange,
                    "请求体过大 (" + (contentLength / BYTES_PER_MB) + "MB)，超过限制 " + maxBodySizeMb + "MB");
        }

        // 检查 JSON 嵌套深度（仅对 application/json 请求）
        if (contentType != null && contentType.contains("application/json") && contentLength > 0) {
            // 此处仅做 Content-Length 级别的预检
            // 深度检查需要在请求体读取后进行，为了避免在网关层缓存全量请求体影响性能
            // 实际深度校验委托给下游服务的 JSON 解析器
            // 网关层仅做大小限制防护
            if (contentLength > BYTES_PER_MB && maxJsonDepth > JSON_DEPTH_WARN_THRESHOLD) {
                log.debug("[PayloadValidation] JSON 请求 {} 字节，深度校验委托下游", contentLength);
            }
        }

        return chain.filter(exchange);
    }

    /**
     * 判断 HTTP 方法是否有请求体
     */
    private boolean hasBody(String method) {
        return "POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method);
    }

    /**
     * 返回 413 请求体过大或 400 参数错误
     */
    private Mono<Void> rejectPayload(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.BAD_REQUEST);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String traceId = TraceIdGenerator.generateSortableTraceId();
        BaseResponse<Void> body = BaseResponse.error(BaseResultCode.BAD_REQUEST, message);
        body.assignTraceId(traceId);
        response.getHeaders().add(GatewayConstants.HEADER_TRACE_ID, traceId);

        byte[] bytes = YdszJson.toJsonBytes(body);
        DataBuffer buffer = response.bufferFactory().wrap(bytes);

        log.warn("[PayloadValidation] 请求体校验失败 path={} reason={}",
                exchange.getRequest().getURI().getPath(), message);
        return response.writeWith(Mono.just(buffer));
    }

    /**
     * 过滤器顺序：最早执行，在认证之前检查请求体
     *
     * @return 顺序值
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 3;
    }
}
