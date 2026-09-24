package com.njydsz.system.server.config;

import com.njydsz.common.safe.filter.IpAccessFilter;
import com.njydsz.common.safe.ip.IpAccessService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 内部 API IP 访问控制配置。
 *
 * <p>对 {@code /api/internal/**} / {@code /internal/**} 路径实施 IP 白名单校验，使用 ydsz-common-safe 的
 * {@link IpAccessFilter}（委托 {@link IpAccessService}），获得 CIDR 网段匹配 + Redis 动态白名单 + 安全事件上报能力。
 *
 * <p><b>配置方式：</b>通过 {@code ydsz.safe.ip-access.includes} 指定生效路径（Ant 风格）。
 *
 * <p><b>过滤器优先级：</b>HIGHEST_PRECEDENCE + 10，确保在鉴权 Filter 之前执行。
 *
 * @author ydsz-team
 * @since 26.09.24
 * @see IpAccessFilter IP 访问控制过滤器
 * @see IpAccessService IP 访问控制服务
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class InternalApiIpAccessConfig {

  private final IpAccessService ipAccessService;

  /**
   * 注册内部 API 专用 IP 访问过滤器。
   *
   * <p>仅对 {@code /internal/*} 和 {@code /api/internal/*} 路径生效，其他路径放行。
   *
   * @return FilterRegistrationBean 实例
   */
  @Bean
  public FilterRegistrationBean<IpAccessFilter> internalApiIpFilterRegistration() {
    IpAccessFilter filter = new IpAccessFilter(ipAccessService, null,
        new java.util.ArrayList<>(0),
        java.util.Arrays.asList("/internal/*", "/api/internal/*"));
    FilterRegistrationBean<IpAccessFilter> registration = new FilterRegistrationBean<>(filter);
    registration.setName("internalApiIpFilter");
    registration.addUrlPatterns("/*");
    registration.setOrder(org.springframework.core.Ordered.HIGHEST_PRECEDENCE + 10);
    return registration;
  }
}
