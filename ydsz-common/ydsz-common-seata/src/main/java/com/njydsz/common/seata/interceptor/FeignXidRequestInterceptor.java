package com.njydsz.common.seata.interceptor;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.common.seata.api.XidPropagator;

/**
 * Feign 请求拦截器 - 上游服务 XID 传播
 *
 * <p>在 Feign 调用时，将当前线程的 XID 写入 HTTP 请求头， 使下游服务可以接续全局事务。
 *
 * <p><b>P0-6 修复</b>：此前 XID 仅 ThreadLocal 存储，Feign 调用时无法传递。
 *
 * <p>仅当类路径存在 {@code feign.RequestInterceptor} 时由 {@link
 * com.njydsz.common.seata.config.SeataAutoConfiguration} 条件注册。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class FeignXidRequestInterceptor implements RequestInterceptor {

  private static final Logger LOG = LoggerFactory.getLogger(FeignXidRequestInterceptor.class);

  private final XidPropagator xidPropagator;

  /**
   * 构造 Feign XID 请求拦截器
   *
   * @param xidPropagator XID 传播器，用于获取当前线程 XID
   */
  public FeignXidRequestInterceptor(XidPropagator xidPropagator) {
    this.xidPropagator = xidPropagator;
  }

  /**
   * 在 Feign 请求发出前，将 XID 写入请求头
   *
   * @param template Feign 请求模板
   */
  @Override
  public void apply(RequestTemplate template) {
    String xid = xidPropagator.currentXid();
    if (xid != null) {
      template.header(XidPropagator.XID_HEADER, xidPropagator.serialize(xid));
      LOG.debug("XID propagated via Feign: {}", xid);
    }
  }
}
