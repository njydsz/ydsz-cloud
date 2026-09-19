package com.njydsz.common.audit.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;

/**
 * 网关审计事件桥接器
 *
 * <p>解决 Spring Cloud Gateway（WebFlux 响应式）与 Spring MVC（Servlet + AOP） 之间的审计数据互通问题。
 *
 * <p>使用场景：
 *
 * <ul>
 *   <li>网关过滤器（{@code GlobalFilter}）无法直接注入 {@code AuditRecorder}（强依赖 Servlet 上下文）
 *   <li>通过本桥接器，网关发布审计事件到 {@link ApplicationEventPublisher}，由 {@link
 *       AuditEventListener}（{@code @Async}）异步消费并落库到 {@code sys_audit_log}
 * </ul>
 *
 * <h3>使用模式：</h3>
 *
 * <pre>{@code
 * // 在 Gateway GlobalFilter 中
 * return chain.filter(exchange).doFinally(signalType -> {
 *     auditEventBridge.publishAuditEvent(
 *         userId,
 *         clientIp,
 *         exchange.getRequest().getMethodValue(),
 *         exchange.getRequest().getURI().getPath(),
 *         exchange.getResponse().getStatusCode() != null
 *             ? exchange.getResponse().getStatusCode().value() : 0,
 *         durationMs,
 *         traceId,
 *         tenantId
 *     );
 * });
 * }</pre>
 *
 * <h3>集成架构：</h3>
 *
 * <pre>
 * Gateway Filter → GatewayAuditEventBridge → ApplicationEventPublisher
 *     → @Async AuditEventListener → AuditRecorder → sys_audit_log
 * </pre>
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>桥接器发布操作日志事件（OperationLogEvent），复用已有的审计消费链路
 *   <li>方法返回 void，调用即忘（fire-and-forget），不引入响应式依赖
 *   <li>内部通过 Spring {@link ApplicationEventPublisher} 发布，由 {@code @Async} 监听器异步处理，
 *       事件发布本身不会阻塞网关请求线程
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public class GatewayAuditEventBridge {

  private static final Logger LOG = LoggerFactory.getLogger(GatewayAuditEventBridge.class);

  private final ApplicationEventPublisher eventPublisher;

  /**
   * 构造网关审计事件桥接器
   *
   * @param eventPublisher 事件发布器
   */
  public GatewayAuditEventBridge(ApplicationEventPublisher eventPublisher) {
    this.eventPublisher = eventPublisher;
  }

  /**
   * 发布网关审计事件（fire-and-forget）
   *
   * <p>从 WebFlux 的非阻塞线程安全发布到 Spring 事件体系。
   * 方法即时返回，事件由 {@code @Async} 监听器异步消费落库，不阻塞网关请求线程。
   *
   * @param userId 用户 ID
   * @param clientIp 客户端 IP
   * @param method HTTP 方法
   * @param path 请求路径
   * @param statusCode HTTP 状态码
   * @param durationMs 请求耗时
   * @param traceId 追踪 ID
   * @param tenantId 租户 ID
   */
  public void publishAuditEvent(
      String userId,
      String clientIp,
      String method,
      String path,
      int statusCode,
      long durationMs,
      String traceId,
      String tenantId) {
    try {
      boolean isWriteOperation = isWriteOperation(method);
      String status = (statusCode >= 200 && statusCode < 400) ? "SUCCESS" : "FAILED";

      // 构建审计事件
      OperationLogEvent event =
          OperationLogEvent.builder()
              .source(this)
              .module("网关路由")
              .action(mapHttpMethod(method))
              .bizType("gateway")
              .bizId(traceId)
              .userId(userId)
              .username(null) // 网关层通常不持有用户名，由下游服务补充
              .requestUrl(path)
              .httpMethod(method)
              .methodSignature("gateway:" + method + " " + path)
              .clientIp(clientIp)
              .userAgent("gateway")
              .paramsJson(null)
              .responseJson(null)
              .beforeData(null)
              .afterData(null)
              .status(status)
              .errorMessage(statusCode >= 400 ? "HTTP " + statusCode : null)
              .costMs(durationMs)
              .traceId(traceId)
              .tenantId(tenantId)
              .build();

      eventPublisher.publishEvent(event);

      if (LOG.isDebugEnabled()) {
        LOG.debug(
            "[GatewayAudit] 审计事件已发布: userId={}, method={}, path={}, status={}, duration={}ms",
            userId,
            method,
            path,
            statusCode,
            durationMs);
      }
    } catch (Exception e) {
      LOG.error("[GatewayAudit] 发布审计事件异常: reason={}", e.getMessage(), e);
    }
  }

  /**
   * 判断 HTTP 方法是否为写操作
   *
   * @param method HTTP 方法名
   * @return 是写操作返回 true
   */
  private boolean isWriteOperation(String method) {
    if (method == null) {
      return false;
    }
    return switch (method.toUpperCase()) {
      case "POST", "PUT", "DELETE", "PATCH" -> true;
      default -> false;
    };
  }

  /**
   * 将 HTTP 方法映射为操作行为字符串
   *
   * @param method HTTP 方法
   * @return 操作行为字符串
   */
  private String mapHttpMethod(String method) {
    if (method == null) {
      return "OTHER";
    }
    return switch (method.toUpperCase()) {
      case "POST" -> "CREATE";
      case "PUT", "PATCH" -> "UPDATE";
      case "DELETE" -> "DELETE";
      case "GET" -> "QUERY";
      default -> "OTHER";
    };
  }
}
