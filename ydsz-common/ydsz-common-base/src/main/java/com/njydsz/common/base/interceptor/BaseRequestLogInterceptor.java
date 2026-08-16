package com.njydsz.common.base.interceptor;

import java.util.concurrent.ThreadLocalRandom;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.njydsz.common.base.config.BaseTraceProperties;
import com.njydsz.common.safe.util.ClientIpResolver;
import com.njydsz.common.util.string.StringUtils;

/**
 * 请求日志拦截器（Web/App 共享）
 *
 * <p>子类覆盖 {@link #resolveRequestId(HttpServletRequest)} 提供不同的 ID 来源， 覆盖 {@link #getLogger()}
 * 提供不同的日志实例。
 *
 * <p><b>日志输出策略：</b>
 *
 * <ul>
 *   <li>{@code preHandle} — 请求入口日志（method/uri/ip/ua）
 *   <li>{@code afterCompletion} — 请求完成日志（status/time/error），含慢请求标记
 *   <li>不在 {@code postHandle} 输出完成日志，避免重复
 * </ul>
 *
 * <p><b>慢请求检测：</b> 当请求耗时超过 {@link BaseTraceProperties#getSlowRequestThreshold()} 时， 日志级别升级为
 * WARN，便于性能监控。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public abstract class BaseRequestLogInterceptor implements HandlerInterceptor, RequestIdResolver {

  private static final String REQUEST_START_TIME = "requestStartTime";

  private final BaseTraceProperties traceProperties;

  protected BaseRequestLogInterceptor(BaseTraceProperties traceProperties) {
    this.traceProperties = traceProperties;
  }

  @Override
  public boolean preHandle(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull Object handler) {
    if (!traceProperties.isRequestLogEnabled()) {
      return true;
    }

    if (ThreadLocalRandom.current().nextDouble() > traceProperties.getSamplingRate()) {
      // 采样跳过：标记请求不输出日志，afterCompletion 中检查此标记跳过
      request.setAttribute("_skipLog", Boolean.TRUE);
      return true;
    }

    long startTime = System.currentTimeMillis();
    request.setAttribute(REQUEST_START_TIME, startTime);

    String method = request.getMethod();
    String uri = request.getRequestURI();
    String queryString = request.getQueryString();
    String clientIp = getClientIp(request);
    String userAgent = request.getHeader("User-Agent");
    String requestId = resolveRequestId(request);

    String fullUri = StringUtils.isNotBlank(queryString) ? uri + "?" + queryString : uri;

    if ("INFO".equalsIgnoreCase(traceProperties.getLogLevel())) {
      getLogger()
          .info(
              "[TRACE] {} {} {} | ip={} | ua={}",
              requestId,
              method,
              fullUri,
              clientIp,
              truncateUserAgent(userAgent));
    } else {
      getLogger()
          .debug(
              "[TRACE] {} {} {} | ip={} | ua={}",
              requestId,
              method,
              fullUri,
              clientIp,
              truncateUserAgent(userAgent));
    }

    return true;
  }

  @Override
  public void postHandle(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull Object handler,
      @Nullable ModelAndView modelAndView) {
    // 不在 postHandle 输出完成日志，统一在 afterCompletion 输出，避免重复
  }

  @Override
  public void afterCompletion(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull Object handler,
      @Nullable Exception ex) {
    if (!traceProperties.isRequestLogEnabled()) {
      return;
    }

    // 采样跳过的请求不输出完成日志
    if (request.getAttribute("_skipLog") != null) {
      return;
    }

    Long startTime = (Long) request.getAttribute(REQUEST_START_TIME);
    long cost = 0L;
    if (startTime != null) {
      cost = System.currentTimeMillis() - startTime;
    }

    String requestId = resolveRequestId(request);
    int status = response.getStatus();
    String uri = request.getRequestURI();
    long slowThreshold = traceProperties.getSlowRequestThreshold();

    if (ex != null) {
      getLogger()
          .error(
              "[TRACE] {} {} | status={} | time={}ms | ERROR: {}",
              requestId,
              uri,
              status,
              cost,
              ex.getMessage());
    } else if (cost > slowThreshold) {
      // 慢请求标记，升级为 WARN
      getLogger()
          .warn(
              "[TRACE] {} {} | status={} | time={}ms | SLOW_REQUEST(>{}ms)",
              requestId,
              uri,
              status,
              cost,
              slowThreshold);
    } else {
      if ("INFO".equalsIgnoreCase(traceProperties.getLogLevel())) {
        getLogger().info("[TRACE] {} {} | status={} | time={}ms", requestId, uri, status, cost);
      } else {
        getLogger().debug("[TRACE] {} {} | status={} | time={}ms", requestId, uri, status, cost);
      }
    }
  }

  /** 子类覆盖此方法提供具体的请求 ID 解析逻辑 */
  @Override
  public abstract String resolveRequestId(HttpServletRequest request);

  /** 子类覆盖此方法提供具体的日志实例 */
  protected abstract Logger getLogger();

  /**
   * 获取客户端 IP 地址。
   *
   * <p>委托 {@link ClientIpResolver#getClientIp} 解析，由 ClientIpResolver 决定 X-Forwarded-For 等代理头与
   * remoteAddr 的取数优先级。
   *
   * @param request HTTP 请求，不可为 {@code null}
   * @return 客户端 IP 地址；无法解析时返回 ClientIpResolver 约定的占位值
   */
  protected String getClientIp(HttpServletRequest request) {
    return ClientIpResolver.getClientIp(request);
  }

  /**
   * 截断过长的 User-Agent 以控制访问日志体积。
   *
   * <p>User-Agent 常被客户端填入极长字符串（指纹特征、恶意构造的探测串），若原样写入日志会 撑爆日志存储并影响检索可读性。统一截断到 100 字符并追加 {@code "..."}
   * 提示已截断。 空/空白 UA 返回占位符 {@code "-"}，保证日志字段始终非空、便于下游解析。
   *
   * @param userAgent 原始 User-Agent 字符串，允许为 {@code null} 或空
   * @return 截断后的 UA，或空 UA 占位符 {@code "-"}
   */
  protected String truncateUserAgent(String userAgent) {
    if (StringUtils.isBlank(userAgent)) {
      return "-";
    }
    if (userAgent.length() > 100) {
      return userAgent.substring(0, 100) + "...";
    }
    return userAgent;
  }
}
