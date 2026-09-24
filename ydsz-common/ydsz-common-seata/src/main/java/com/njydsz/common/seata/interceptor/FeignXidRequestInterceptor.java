package com.njydsz.common.seata.interceptor;

import com.njydsz.common.seata.aspect.SeataReflector;
import com.njydsz.common.seata.config.SeataProperties;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Feign 调用端 XID 透传拦截器（发出端）。
 *
 * <p>在 Feign 发出请求前，读取当前线程通过 {@link
 * com.njydsz.common.seata.filter.XidServletFilter} 绑定的 XID，并通过 Header
 * 注入到请求中（Header 名称由 {@link SeataProperties#getXidHeaderName()} 指定，
 * 默认 {@code TX_XID}，上下游需一致）。
 *
 * <p>启用条件（任一不满足时本 Interceptor 不被注册）：
 * <ul>
 *   <li>{@code ydsz.seata.enabled=true} 且 {@code ydsz.seata.xidInterceptorEnabled=true}；</li>
 *   <li> classpath 存在 Feign（spring-cloud-starter-openfeign，optional）；</li>
 *   <li> classpath 存在 Seata 客户端（io.seata.core.context.RootContext）。</li>
 * </ul>
 *
 * <p>禁止业务方手动覆盖默认拦截器导致 XID 透传链断裂（YDIZ-TX-004）；如需
 * 自定义 Feign {@code @Configuration}，必须确保本拦截器的完整调用链存在。
 *
 * @author ydsz-team
 * @since ACC-1
 * @see com.njydsz.common.seata.filter.XidServletFilter
 */
@Slf4j
@RequiredArgsConstructor
public class FeignXidRequestInterceptor implements RequestInterceptor {

  private final SeataProperties properties;

  @Override
  public void apply(final RequestTemplate template) {
    if (!properties.isXidInterceptorEnabled()) {
      return;
    }
    if (!SeataReflector.seataPresent()) {
      return;
    }
    final String xid = SeataReflector.getXid();
    if (xid != null && !xid.isEmpty()) {
      template.header(properties.getXidHeaderName(), xid);
      log.debug("Feign 请求注入 XID, header={}, xid={}", properties.getXidHeaderName(), xid);
    }
  }
}
