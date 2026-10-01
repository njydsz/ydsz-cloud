package com.njydsz.common.web.filter;

import java.io.IOException;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.slf4j.MDC;

import com.njydsz.common.core.context.RequestContext;

/**
 * 租户 MDC 过滤器
 *
 * <p>在请求处理前将 tenantId 写入 MDC（Mapped Diagnostic Context）， 使得日志输出中自动包含租户标识，便于按租户维度排查问题。
 *
 * <p><b>装配方式：</b>由 {@code WebCoreAutoConfiguration} 通过 {@code @Bean} + {@code @ConditionalOnMissingBean}
 * 注册，无需手动装配。
 *
 * <p>可通过 {@code ydsz.core.tenant-mdc-filter.enabled=false} 关闭。
 *
 * <p><b>日志配置示例（logback-spring.xml）：</b>
 *
 * <pre>{@code
 * &lt;pattern&gt;%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] [%X{tenantId}] %-5level %logger{36} - %msg%n&lt;/pattern&gt;
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.10.01
 */
public class TenantMdcFilter implements Filter {

  /** MDC 键名：租户 ID */
  public static final String MDC_TENANT_ID = "tenantId";

  /** MDC 键名：用户 ID */
  public static final String MDC_USER_ID = "userId";

  @Override
  public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
      throws IOException, ServletException {
    try {
      String tenantId = RequestContext.getTenantId();
      String userId = RequestContext.getUserId();
      if (tenantId != null && !tenantId.isEmpty()) {
        MDC.put(MDC_TENANT_ID, tenantId);
      }
      if (userId != null && !userId.isEmpty()) {
        MDC.put(MDC_USER_ID, userId);
      }
      chain.doFilter(request, response);
    } finally {
      MDC.remove(MDC_TENANT_ID);
      MDC.remove(MDC_USER_ID);
    }
  }
}
