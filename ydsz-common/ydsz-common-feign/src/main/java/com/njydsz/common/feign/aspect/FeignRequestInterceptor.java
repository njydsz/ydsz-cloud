package com.njydsz.common.feign.aspect;

import com.njydsz.common.feign.config.FeignProperties;
import com.njydsz.common.util.http.RequestContextUtils;
import com.njydsz.common.util.id.TracerUtils;
import com.njydsz.common.util.string.StringUtils;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Feign核心请求头透传拦截器
 *
 * <p>透传13个高频业务常用请求头，覆盖所有业务场景需求： 【链路追踪】traceparent：W3C标准链路追踪头 【租户隔离】X-Tenant-Id：租户上下文标识
 * 【身份鉴权】X-Access-Token：用户访问令牌、X-Service-Type：服务类型标识、X-User-Userid：当前用户ID、X-User-Username：当前用户名、X-Unique-Id：用户登录唯一ID
 * 【权限校验】X-Data-Scope：数据权限范围类型、X-Company-Ids：公司ID集合、X-Dept-Ids：部门ID集合
 * 【业务通用】X-User-Locale：用户语言环境（国际化）、X-Request-Source：请求来源标识、X-Request-Id：请求唯一标识（不存在时自动生成）
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class FeignRequestInterceptor implements RequestInterceptor {

  private static final Logger log = LoggerFactory.getLogger(FeignRequestInterceptor.class);

  private final FeignProperties feignProperties;

  public FeignRequestInterceptor(FeignProperties feignProperties) {
    this.feignProperties = feignProperties;
  }

  @Override
  public void apply(RequestTemplate requestTemplate) {
    if (feignProperties == null
        || feignProperties.getPropagation() == null
        || !feignProperties.getPropagation().isEnabled()
        || feignProperties.getPropagation().getHeaders() == null
        || feignProperties.getPropagation().getHeaders().isEmpty()) {
      return;
    }

    HttpServletRequest request = RequestContextUtils.getRequest();
    Set<String> headersToPropagate = feignProperties.getPropagation().getHeaders();

    // 透传链路追踪头
    if (headersToPropagate.contains("traceparent") && !hasHeader(requestTemplate, "traceparent")) {
      requestTemplate.header("traceparent", TracerUtils.getCurrentTraceParent());
    }

    // 透传租户/身份/权限相关常用头
    propagateSimpleHeader(requestTemplate, request, headersToPropagate, "X-Tenant-Id");
    propagateSimpleHeader(requestTemplate, request, headersToPropagate, "X-Access-Token");
    propagateSimpleHeader(requestTemplate, request, headersToPropagate, "X-User-Userid");
    propagateSimpleHeader(requestTemplate, request, headersToPropagate, "X-User-Username");
    propagateSimpleHeader(requestTemplate, request, headersToPropagate, "X-User-Locale");
    propagateSimpleHeader(requestTemplate, request, headersToPropagate, "X-Request-Source");
    propagateSimpleHeader(requestTemplate, request, headersToPropagate, "X-Company-Ids");
    propagateSimpleHeader(requestTemplate, request, headersToPropagate, "X-Data-Scope");
    propagateSimpleHeader(requestTemplate, request, headersToPropagate, "X-Unique-Id");
    propagateSimpleHeader(requestTemplate, request, headersToPropagate, "X-Dept-Ids");
    propagateSimpleHeader(requestTemplate, request, headersToPropagate, "X-Service-Type");

    // 处理请求ID透传，不存在时自动生成
    if (headersToPropagate.contains("X-Request-Id")
        && !hasHeader(requestTemplate, "X-Request-Id")) {
      String requestId = request != null ? request.getHeader("X-Request-Id") : null;
      if (StringUtils.isEmpty(requestId)) {
        requestId = TracerUtils.getTraceId();
        if (StringUtils.isEmpty(requestId)) {
          requestId = TracerUtils.generateTraceId();
        }
      }
      requestTemplate.header("X-Request-Id", requestId);
    }
  }

  /** 透传简单类型的请求头，从HttpServletRequest获取后写入 */
  private void propagateSimpleHeader(
      RequestTemplate requestTemplate,
      HttpServletRequest request,
      Set<String> headersToPropagate,
      String headerName) {
    if (!headersToPropagate.contains(headerName) || hasHeader(requestTemplate, headerName)) {
      return;
    }
    String value = request != null ? request.getHeader(headerName) : null;
    if (StringUtils.isNotEmpty(value)) {
      requestTemplate.header(headerName, value);
    }
  }

  /** 判断请求头是否已存在 */
  private boolean hasHeader(RequestTemplate requestTemplate, String headerName) {
    return requestTemplate.headers() != null
        && requestTemplate.headers().get(headerName) != null
        && !requestTemplate.headers().get(headerName).isEmpty();
  }
}
