package com.njydsz.common.feign.aspect;

import java.util.Set;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.common.feign.config.FeignProperties;
import com.njydsz.common.util.http.RequestContextUtils;
import com.njydsz.common.util.id.TracerUtils;
import com.njydsz.common.util.string.StringUtils;

/**
 * Feign 核心请求头透传拦截器
 *
 * <p>透传 13 个高频业务常用请求头，覆盖所有业务场景需求：
 *
 * <ul>
 *   <li>【链路追踪】traceparent：W3C 标准链路追踪头
 *   <li>【租户隔离】X-Tenant-Id：租户上下文标识
 *   <li>【身份鉴权】X-Access-Token：用户访问令牌 / X-Service-Type：服务类型标识 /
 *       X-User-Userid：当前用户 ID / X-User-Username：当前用户名 / X-Unique-Id：用户登录唯一 ID
 *   <li>【权限校验】X-Data-Scope：数据权限范围类型 / X-Company-Ids：公司 ID 集合 / X-Dept-Ids：部门 ID 集合
 *   <li>【业务通用】X-User-Locale：用户语言环境（国际化）/ X-Request-Source：请求来源标识 /
 *       X-Request-Id：请求唯一标识
 * </ul>
 *
 * <p><b>去重逻辑（自 26.09.19）：</b>透传前检查目标 Header 是否已被写入（如 ydsz-common-tenant 模块的
 * TenantContextFeignInterceptor 可能已写入 X-Tenant-Id），避免重复覆盖。已存在的 Header 不会被覆盖，
 * 保证多拦截器协作时后者优先。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public class FeignRequestInterceptor implements RequestInterceptor {

  private static final Logger LOG = LoggerFactory.getLogger(FeignRequestInterceptor.class);

  /** 标准透传头列表（固定顺序，X-Request-Id 单独处理） */
  private static final String[] STANDARD_HEADERS = {
    "X-Tenant-Id",
    "X-Access-Token",
    "X-User-Userid",
    "X-User-Username",
    "X-User-Locale",
    "X-Request-Source",
    "X-Company-Ids",
    "X-Data-Scope",
    "X-Unique-Id",
    "X-Dept-Ids",
    "X-Service-Type"
  };

  private final FeignProperties feignProperties;

  public FeignRequestInterceptor(FeignProperties feignProperties) {
    this.feignProperties = feignProperties;
  }

  @Override
  public void apply(RequestTemplate requestTemplate) {
    if (!isPropagationEnabled()) {
      return;
    }

    HttpServletRequest request = RequestContextUtils.getRequest();
    Set<String> headersToPropagate = feignProperties.getPropagation().getHeaders();

    // 透传链路追踪头
    if (headersToPropagate.contains("traceparent") && !hasHeader(requestTemplate, "traceparent")) {
      String traceParent = TracerUtils.getCurrentTraceParent();
      if (StringUtils.isNotEmpty(traceParent)) {
        requestTemplate.header("traceparent", traceParent);
      }
    }

    // 透传租户 / 身份 / 权限等 11 个核心头
    for (String headerName : STANDARD_HEADERS) {
      propagateSimpleHeader(requestTemplate, request, headersToPropagate, headerName);
    }

    // 处理请求 ID 透传（不存在时自动生成）
    propagateRequestId(requestTemplate, request, headersToPropagate);
  }

  /**
   * 透传简单类型的请求头，从 HttpServletRequest 获取后写入。
   *
   * <p>去重逻辑：若目标头已存在于 RequestTemplate（如其它拦截器已写入），则不覆盖，保证多拦截器协作时后者优先。
   *
   * @param requestTemplate Feign 请求模板
   * @param request 当前 HTTP 请求
   * @param headersToPropagate 白名单集合
   * @param headerName 请求头名称
   */
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

  /**
   * 透传请求 ID：优先从请求上下文获取，不存在时尝试 TraceId，最后自动生成。
   *
   * <p>自动生成的请求 ID 用于端到端链路串联，当上游未携带 X-Request-Id 时使用 TraceId 兜底。
   *
   * @param requestTemplate Feign 请求模板
   * @param request 当前 HTTP 请求
   * @param headersToPropagate 白名单集合
   */
  private void propagateRequestId(
      RequestTemplate requestTemplate,
      HttpServletRequest request,
      Set<String> headersToPropagate) {
    if (!headersToPropagate.contains("X-Request-Id")
        || hasHeader(requestTemplate, "X-Request-Id")) {
      return;
    }
    String requestId = request != null ? request.getHeader("X-Request-Id") : null;
    if (StringUtils.isEmpty(requestId)) {
      requestId = TracerUtils.getTraceId();
    }
    if (StringUtils.isEmpty(requestId)) {
      requestId = TracerUtils.generateTraceId();
    }
    requestTemplate.header("X-Request-Id", requestId);
  }

  /**
   * 判断请求头是否已存在（去重检查）。
   *
   * <p>当其他拦截器（如 TenantContextFeignInterceptor）已写入相同头的值时，返回 true，防止覆盖。
   *
   * @param requestTemplate Feign 请求模板
   * @param headerName 请求头名称
   * @return true 表示已存在
   */
  private boolean hasHeader(RequestTemplate requestTemplate, String headerName) {
    return requestTemplate != null
        && requestTemplate.headers() != null
        && requestTemplate.headers().get(headerName) != null
        && !requestTemplate.headers().get(headerName).isEmpty();
  }

  /** 请求头透传是否启用 */
  private boolean isPropagationEnabled() {
    return feignProperties != null
        && feignProperties.getPropagation() != null
        && feignProperties.getPropagation().isEnabled()
        && feignProperties.getPropagation().getHeaders() != null
        && !feignProperties.getPropagation().getHeaders().isEmpty();
  }
}
