package com.njydsz.common.seata.filter;

import com.njydsz.common.seata.aspect.SeataReflector;
import com.njydsz.common.seata.config.SeataProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 服务端 XID 入口过滤器（接收端）。
 *
 * <p>在请求进入时（{@code doFilterInternal}）解析下游通过 {@link
 * com.njydsz.common.seata.config.SeataProperties#getXidHeaderName()}
 * 指定的 Header（默认 {@code TX_XID}），并通过反射调用 {@code io.seata.core.context.RootContext#bind}
 * 绑定到当前线程，以跨 Feign 调用链全局事务上下文；请求完成后（finally）执行 unbind 避免串扰。
 *
 * <p>启用条件：
 * <ul>
 *   <li>{@code ydsz.seata.enabled=true} 且 {@code ydsz.seata.xidFilterEnabled=true}；</li>
 *   <li>classpath 存在 Seata 客户端（Runtime classpath）。</li>
 * </ul>
 * 任一条件不满足时，本 Filter 不被注册，业务方无感知。
 *
 * <p>业务方禁止通过 {@code server.servlet} 过滤规则覆盖本 Filter 的 XID Header
 * （YDIZ-TX-004）。
 *
 * @author ydsz-team
 * @since ACC-1
 * @see com.njydsz.common.seata.interceptor.FeignXidRequestInterceptor
 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE + 100)
@RequiredArgsConstructor
public class XidServletFilter extends OncePerRequestFilter {

  private final SeataProperties properties;

  @Override
  protected boolean shouldNotFilter(final HttpServletRequest request) {
    return !properties.isXidFilterEnabled() || !SeataReflector.seataPresent();
  }

  @Override
  protected void doFilterInternal(
      final HttpServletRequest request,
      final HttpServletResponse response,
      final FilterChain filterChain)
      throws ServletException, IOException {
    final String xid = request.getHeader(properties.getXidHeaderName());
    try {
      if (xid != null && !xid.isEmpty()) {
        SeataReflector.bindXid(xid);
        log.debug("已绑定下游传入 XID, header={}, xid={}", properties.getXidHeaderName(), xid);
      }
      filterChain.doFilter(request, response);
    } finally {
      SeataReflector.unbindXid();
    }
  }
}
