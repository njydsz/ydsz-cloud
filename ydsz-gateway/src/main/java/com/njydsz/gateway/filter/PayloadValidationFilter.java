package com.njydsz.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import com.njydsz.gateway.config.GatewayConstants;
import com.njydsz.gateway.config.GatewayErrorCode;
import com.njydsz.gateway.config.GatewayErrorWriter;
import com.njydsz.gateway.config.GatewayFilterOrder;

/**
 * P1-8: 请求体安全校验过滤器
 *
 * <p>在网关层校验请求体大小与 Content-Type，防止恶意请求导致 OOM。
 *
 * <h3>校验项</h3>
 *
 * <ul>
 *   <li>请求体大小限制（可配置，默认 10MB）
 *   <li>Content-Type 严格校验（POST/PUT/PATCH 必须指定 Content-Type）
 * </ul>
 *
 * <p><b>职责边界（P0-B2）：</b>网关层<b>仅</b>做传输层防护（大小 + Content-Type 预检），
 * 不做 JSON 内容级校验——读取并缓存全量请求体做深度/Schema 校验会引入额外内存拷贝与性能损耗。
 * JSON 嵌套深度、字段校验由下游服务解析器负责（其本身就具备递归深度保护）。历史配置项
 * {@code max-json-depth} 为死配置，已移除。
 *
 * <p><b>与 default-filter RequestSize 的分工：</b>{@code spring.cloud.gateway.default-filters}
 * 中的 {@code RequestSize} 对全部请求（含 GET）做传输层上限拦截；本过滤器仅对 POST/PUT/PATCH
 * 做 Content-Type 与大小预检，两者互补不冲突。
 *
 * <h3>配置方式</h3>
 *
 * <pre>
 * ydsz:
 *   gateway:
 *     payload-validation:
 *       enabled: true
 *       max-body-size-mb: 10           # 请求体最大大小（MB）
 *       strict-content-type: true      # 是否强制校验 Content-Type
 * </pre>
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@Slf4j
@Component
@ConditionalOnProperty(
    prefix = "ydsz.gateway.filter",
    name = "payload-validation",
    havingValue = "true",
    matchIfMissing = true)
public class PayloadValidationFilter implements GlobalFilter, Ordered {

  @Value("${ydsz.gateway.payload-validation.enabled:true}")
  private boolean enabled;

  @Value("${ydsz.gateway.payload-validation.max-body-size-mb:10}")
  private int maxBodySizeMb;

  @Value("${ydsz.gateway.payload-validation.strict-content-type:true}")
  private boolean strictContentType;

  private static final long BYTES_PER_MB = 1024L * 1024L;

  /**
   * 请求体安全校验过滤器：限制请求体大小与 Content-Type。
   *
   * <p>仅对 POST/PUT/PATCH 等有请求体的方法生效；校验 Content-Type 是否缺失、 请求体是否超过 {@code max-body-size-mb}（默认
   * 10MB），超限返回 400。
   *
   * @param exchange 服务器 Web 交换上下文
   * @param chain 网关过滤器链
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
      return rejectPayload(
          exchange,
          GatewayErrorCode.CONTENT_TYPE_MISSING,
          "Content-Type 缺失，POST/PUT/PATCH 请求必须指定 Content-Type");
    }

    // 检查请求体大小（Content-Length 预检；无 Content-Length 的 chunked 请求由
    // default-filter RequestSize 在传输层兜底）
    long contentLength = request.getHeaders().getContentLength();
    long maxBytes = maxBodySizeMb * BYTES_PER_MB;
    if (contentLength > maxBytes) {
      return rejectPayload(
          exchange,
          GatewayErrorCode.PAYLOAD_TOO_LARGE,
          "请求体过大 (" + (contentLength / BYTES_PER_MB) + "MB)，超过限制 " + maxBodySizeMb + "MB");
    }

    return chain.filter(exchange);
  }

  /** 判断 HTTP 方法是否有请求体 */
  private boolean hasBody(String method) {
    return "POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method);
  }

  /** 返回 4xx 请求体校验失败响应（P0-D1：统一错误响应写出器）。 */
  private Mono<Void> rejectPayload(
      ServerWebExchange exchange, GatewayErrorCode errorCode, String message) {
    String traceId = exchange.getRequest().getHeaders().getFirst(GatewayConstants.HEADER_TRACE_ID);

    log.warn(
        "[PayloadValidation] 请求体校验失败 path={} reason={}",
        exchange.getRequest().getURI().getPath(),
        message);
    return GatewayErrorWriter.write(exchange, HttpStatus.BAD_REQUEST, errorCode, message, traceId);
  }

  /**
   * 过滤器顺序：最早执行，在认证之前检查请求体
   *
   * @return 顺序值
   */
  @Override
  public int getOrder() {
    return GatewayFilterOrder.PAYLOAD_VALIDATION.getOrder();
  }
}
