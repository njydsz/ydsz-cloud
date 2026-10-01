package com.njydsz.gateway.filter;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import com.njydsz.common.safe.ip.IpAccessService;
import com.njydsz.gateway.config.GatewayErrorCode;
import com.njydsz.gateway.config.GatewayFilterOrder;
import com.njydsz.gateway.config.GatewayIpUtils;
import com.njydsz.gateway.exception.GatewayErrorWriter;

/**
 * IP 访问控制全局过滤器（Delegating to ydsz-common-safe IpAccessService）。
 *
 * <p>替代网关原有的 {@code IpAccessControlFilter}（自建逻辑），统一委托 ydzz-common-safe
 * {@link IpAccessService#isAllowed(String)} 完成 IP 黑白名单校验。
 * 获得以下公共能力复用：
 *
 * <ul>
 *   <li>CIDR 网段匹配（如 10.0.0.0/8）</li>
 *   <li>Redis 动态黑名单/白名单（实时生效，分布式共享）</li>
 *   <li>本地缓存（降低 Redis 查询延迟）</li>
 *   <li>WHITELIST / BLACKLIST 双模式</li>
 *   <li>安全事件上报（through IpAccessService internal triggers）</li>
 * </ul>
 *
 * <p><b>执行顺序：</b>{@code HIGHEST_PRECEDENCE + 3}，在限流(+50)和鉴权(+10)之前执行，
 * 尽早拦截恶意 IP 避免浪费限流配额。
 *
 * <p><b>启用条件（AND）：</b>
 * <ol>
 *   <li>Spring 容器中存在 {@link IpAccessService} Bean（即 ydsz-common-safe 模块已装配）</li>
 *   <li>配置属性 {@code ydsz.gateway.filter.ip-access.enabled=true}</li>
 * </ol>
 *
 * <p><b>降级策略：</b>{@link IpAccessService#isAllowed} 内部异常时 fail-open（返回 true），
 * 仅记录日志，不阻断请求。
 *
 * @since 26.09.30
 * @author ydsz-team
 * @see IpAccessService
 * @see GatewayIpAccessConfig
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnBean(IpAccessService.class)
@ConditionalOnProperty(
    prefix = "ydsz.gateway.filter",
    name = "ip-access",
    havingValue = "true",
    matchIfMissing = false)
public class IpAccessGlobalFilter implements GlobalFilter, Ordered {

  private final IpAccessService ipAccessService;

  /** 阻塞调用调度策略（boundedElastic，适配 IpAccessService 同步 Redis 调用）。 */
  private final Scheduler blockingScheduler = Schedulers.newBoundedElastic(
      50, 1000, "ip-access-safe", 60, true);

  /**
   * IP 访问控制过滤器入口。
   *
   * <p>提取客户端 IP 后委托 {@link IpAccessService#isAllowed(String)} 判定，
   * 拒绝时返回 403 + 统一错误响应。
   *
   * @param exchange 服务器 Web 交换上下文
   * @param chain 网关过滤器链
   * @return 放行或拒绝（403）的完成信号 Mono
   */
  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    ServerHttpRequest request = exchange.getRequest();
    String clientIp = GatewayIpUtils.getClientIp(request);

    // 无法获取 IP 时放行（由 RateLimitFilter 后续处理）
    if (clientIp == null || clientIp.isEmpty() || "0.0.0.0".equals(clientIp)) {
      return chain.filter(exchange);
    }

    return Mono.fromCallable(() -> ipAccessService.isAllowed(clientIp))
        .subscribeOn(blockingScheduler)
        .flatMap(allowed -> {
          if (Boolean.TRUE.equals(allowed)) {
            return chain.filter(exchange);
          }
          log.warn("[IpAccess] IP 被拒绝: ip={}, path={}", clientIp, request.getURI().getPath());
          return GatewayErrorWriter.write(
              exchange,
              HttpStatus.FORBIDDEN,
              GatewayErrorCode.IP_BLACKLISTED,
              "error.IP_FORBIDDEN");
        })
        .onErrorResume(e -> {
          log.warn("[IpAccess] IP 检查异常，降级放行: ip={}, err={}", clientIp, e.getMessage());
          return chain.filter(exchange);
        });
  }

  @Override
  public int getOrder() {
    return GatewayFilterOrder.IP_ACCESS_CONTROL.getOrder();
  }
}
