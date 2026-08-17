package com.njydsz.gateway.filter;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import com.njydsz.common.audit.event.GatewayAuditEventBridge;
import com.njydsz.common.jdbc.constant.DataPermissionHeaderConstants;
import com.njydsz.gateway.config.GatewayConstants;
import com.njydsz.gateway.config.GatewayFilterOrder;

/**
 * P2-2: 审计日志过滤器
 *
 * <p>记录安全相关的操作日志，满足合规审计要求（如等保/GDPR）。
 *
 * <h3>审计范围</h3>
 *
 * <ul>
 *   <li>所有非 GET 请求（POST/PUT/DELETE/PATCH）— 写操作
 *   <li>认证相关请求（/auth/**、/api/login 等）— 身份事件
 *   <li>管理类接口（/admin/**、/api/admin/**）— 权限变更
 *   <li>安全事件（401/403 响应）— 越权尝试
 * </ul>
 *
 * <h3>审计内容</h3>
 *
 * <ul>
 *   <li>操作时间（UTC ISO-8601）
 *   <li>操作人（userId / IP / sessionId）
 *   <li>操作类型（HTTP method + path）
 *   <li>操作结果（HTTP status）
 *   <li>用户设备（User-Agent / 客户端指纹）
 * </ul>
 *
 * <h3>审计链路（v1.2.0 双轨制）</h3>
 *
 * <p>自 v1.2.0 起，网关审计采用双轨输出：
 *
 * <ol>
 *   <li><b>SLF4J 结构化日志</b>（保留）：输出到 ELK/Loki，用于日志检索与告警
 *   <li><b>审计事件桥接</b>（新增）：通过 {@link GatewayAuditEventBridge} 发布操作日志事件， 由 ydsz-common-audit 的 {@code
 *       AuditEventListener} 消费并落库到 sys_audit_log， 实现与业务模块审计数据的统一存储
 * </ol>
 *
 * <h3>执行顺序</h3>
 *
 * <p>{@code HIGHEST_PRECEDENCE + 35}，在限流(+30)之后， 在 AccessLogGlobalFilter 之后（使用其 traceId 关联）。
 *
 * @since 1.0.0 (P2-2)
 * @author ydsz-team
 */
@Slf4j
@Component
@ConditionalOnProperty(
    prefix = "ydsz.gateway.filter",
    name = "audit-log",
    havingValue = "true",
    matchIfMissing = true)
public class AuditLogFilter implements GlobalFilter, Ordered {

  /** 审计敏感路径前缀（写操作/管理接口/鉴权接口） */
  private static final Set<String> AUDIT_SENSITIVE_PREFIXES =
      Set.of(
          "/auth",
          "/api/admin",
          "/admin",
          "/api/role",
          "/api/permission",
          "/api/user/create",
          "/api/user/delete",
          "/api/tenant",
          "/api/apikey");

  /** 高敏感 HTTP 方法（DELETE 删除、PATCH 部分更新、PUT 全量更新） */
  private static final Set<HttpMethod> HIGH_SENSITIVITY_METHODS =
      Set.of(HttpMethod.DELETE, HttpMethod.PATCH, HttpMethod.PUT);

  /**
   * 网关审计事件桥接器（可选 Bean）。
   *
   * <p>当容器中存在 {@link GatewayAuditEventBridge} 时（项目引入 ydzz-common-audit）， 审计事件会同步发布到 Spring
   * 事件体系，最终落库到 sys_audit_log。 不存在时仅输出 SLF4J 结构化日志，保持对 audit 模块的松耦合。
   *
   * <p>使用 {@code @Autowired(required = false)} 适配无 common-audit 的项目。
   */
  @Autowired(required = false)
  private GatewayAuditEventBridge gatewayAuditEventBridge;

  /**
   * 审计日志过滤器入口
   *
   * <p>在请求完成后记录审计日志，使用 {@code then()} 确保 审计不影响正常请求处理。
   *
   * @param exchange 服务器 Web 交换上下文
   * @param chain 网关过滤器链
   * @return 放行后的完成信号
   */
  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    ServerHttpRequest request = exchange.getRequest();
    HttpMethod method = request.getMethod();
    String path = request.getURI().getPath();

    // 判断是否需要审计
    boolean shouldAudit = shouldAuditRequest(method, path);

    if (!shouldAudit) {
      return chain.filter(exchange);
    }

    // 记录请求开始时间
    long startTime = System.currentTimeMillis();

    return chain
        .filter(exchange)
        .then(
            Mono.fromRunnable(
                () -> {
                  // 请求完成后记录审计日志
                  int statusCode =
                      exchange.getResponse().getStatusCode() != null
                          ? exchange.getResponse().getStatusCode().value()
                          : 0;
                  long duration = System.currentTimeMillis() - startTime;

                  // 轨道 1：SLF4J 结构化日志（保留）
                  writeStructuredLog(exchange, method, path, statusCode, duration);

                  // 轨道 2：审计事件桥接（v1.2.0 新增，可选）
                  publishAuditEvent(exchange, method, path, statusCode, duration);
                }));
  }

  /**
   * 轨道 1：输出 SLF4J 结构化日志（key=value 格式，ELK/Loki 原生解析）。
   *
   * <p>使用 SLF4J key=value 格式替代手动 JSON 拼接，避免转义漏洞，日志平台可直接按字段检索。
   *
   * @param exchange 服务器 Web 交换上下文
   * @param method HTTP 方法
   * @param path 请求路径
   * @param statusCode HTTP 状态码
   * @param duration 请求耗时（毫秒）
   */
  private void writeStructuredLog(
      ServerWebExchange exchange, HttpMethod method, String path, int statusCode, long duration) {
    ServerHttpRequest request = exchange.getRequest();

    String userId = request.getHeaders().getFirst(GatewayConstants.HEADER_USER_ID);
    String traceId = request.getHeaders().getFirst(GatewayConstants.HEADER_TRACE_ID);
    String clientIp = extractClientIp(request);
    String userAgent = request.getHeaders().getFirst("User-Agent");
    String tenantId = request.getHeaders().getFirst(DataPermissionHeaderConstants.X_TENANT_ID);

    // User-Agent 截断
    if (userAgent != null && userAgent.length() > 100) {
      userAgent = userAgent.substring(0, 100);
    }

    // 敏感级别：HIGH（高敏感操作）/ MEDIUM（一般写操作）/ LOW（安全事件如 401）
    String sensitivity = resolveSensitivity(method, path, statusCode);

    // 脱敏路径（移除敏感信息）
    String sanitizedPath = sanitizePath(path);

    // 使用 SLF4J key=value 结构化格式（日志平台原生解析）
    Map<String, String> fields = new HashMap<>();
    fields.put("eventType", "AUDIT");
    fields.put("traceId", safeValue(traceId));
    fields.put("userId", safeValue(userId));
    fields.put("tenantId", safeValue(tenantId));
    fields.put("clientIp", safeValue(clientIp));
    fields.put("method", method.name());
    fields.put("path", sanitizedPath);
    fields.put("status", String.valueOf(statusCode));
    fields.put("durationMs", String.valueOf(duration));
    fields.put("userAgent", safeValue(userAgent));
    fields.put("sensitivity", sensitivity);

    // 格式: eventType=AUDIT traceId=xxx userId=xxx ...
    String structuredLog = formatKeyValueLog(fields);

    // 按敏感级别和响应状态选择日志级别
    if ("HIGH".equals(sensitivity) || statusCode == 401 || statusCode == 403) {
      log.warn(structuredLog);
    } else {
      log.info(structuredLog);
    }
  }

  /**
   * 将字段 Map 格式化为 key=value 结构化日志字符串。
   *
   * <p>符合 ELK/Loki 结构化日志标准格式，可直接按字段检索聚合。
   *
   * @param fields 日志字段映射
   * @return key=value 格式字符串
   */
  private String formatKeyValueLog(Map<String, String> fields) {
    StringBuilder sb = new StringBuilder();
    fields.forEach((key, value) -> {
      if (sb.length() > 0) {
        sb.append(' ');
      }
      sb.append(key).append('=').append(value);
    });
    return sb.toString();
  }

  /**
   * Null 安全的值处理。
   *
   * @param value 原始值（可为 null）
   * @return 非 null 字符串
   */
  private String safeValue(String value) {
    return (value == null || value.isEmpty()) ? "-" : value;
  }

  /**
   * 轨道 2：发布审计事件到 sys_audit_log
   *
   * <p>通过 {@link GatewayAuditEventBridge} 将网关审计数据桥接至 ydsz-common-audit 模块， 由模块内部的 {@code
   * AuditEventListener} 异步消费并落库到 sys_audit_log 表。 实现网关与业务模块审计数据的统一存储。
   *
   * <p>当 {@link GatewayAuditEventBridge} 不存在于容器时（项目未引入 common-audit），静默跳过。
   *
   * @param exchange 服务器 Web 交换上下文
   * @param method HTTP 方法
   * @param path 请求路径
   * @param statusCode HTTP 状态码
   * @param duration 请求耗时（毫秒）
   */
  private void publishAuditEvent(
      ServerWebExchange exchange, HttpMethod method, String path, int statusCode, long duration) {
    if (gatewayAuditEventBridge == null) {
      return;
    }

    try {
      ServerHttpRequest request = exchange.getRequest();
      String userId = request.getHeaders().getFirst(GatewayConstants.HEADER_USER_ID);
      String traceId = request.getHeaders().getFirst(GatewayConstants.HEADER_TRACE_ID);
      String clientIp = extractClientIp(request);
      String tenantId = request.getHeaders().getFirst(DataPermissionHeaderConstants.X_TENANT_ID);

      // 发布审计事件（异步消费，不阻塞响应式线程）
      gatewayAuditEventBridge
          .publishAuditEvent(
              userId, clientIp, method.name(), path, statusCode, duration, traceId, tenantId)
          .subscribe();
    } catch (Exception e) {
      // 审计事件发布异常不影响主链路
      log.debug("[AuditLogFilter] 审计事件发布异常（非致命）: {}", e.getMessage());
    }
  }

  /**
   * 判断请求是否需要审计
   *
   * @param method HTTP 方法
   * @param path 请求路径
   * @return true 如果需要审计
   */
  private boolean shouldAuditRequest(HttpMethod method, String path) {
    // 高敏感方法（写操作/删除）一律审计
    if (HIGH_SENSITIVITY_METHODS.contains(method)) {
      return true;
    }

    // POST 请求审计（资源创建）
    if (HttpMethod.POST.equals(method)) {
      return true;
    }

    // 敏感路径审计（无论 GET/POST）
    return AUDIT_SENSITIVE_PREFIXES.stream().anyMatch(path::startsWith);
  }

  /**
   * 解析审计敏感级别
   *
   * @param method HTTP 方法
   * @param path 请求路径
   * @param statusCode 响应状态码
   * @return 敏感级别字符串
   */
  private String resolveSensitivity(HttpMethod method, String path, int statusCode) {
    // 安全事件（401/403）→ LOW（已单独按级别输出）
    if (statusCode == 401 || statusCode == 403) {
      return "LOW";
    }

    // 高敏感 DELETE/PUT 或管理路径
    if (HIGH_SENSITIVITY_METHODS.contains(method)
        || AUDIT_SENSITIVE_PREFIXES.stream().anyMatch(path::startsWith)) {
      return "HIGH";
    }

    // 其他写操作
    return "MEDIUM";
  }

  /**
   * 提取客户端 IP
   *
   * @param request 服务器 HTTP 请求
   * @return 客户端 IP 字符串
   */
  private String extractClientIp(ServerHttpRequest request) {
    // 优先 X-Real-IP / X-Forwarded-For
    String xff = request.getHeaders().getFirst("X-Real-IP");
    if (xff != null && !xff.isEmpty()) {
      return xff.split(",")[0].trim();
    }
    xff = request.getHeaders().getFirst("X-Forwarded-For");
    if (xff != null && !xff.isEmpty()) {
      return xff.split(",")[0].trim();
    }
    // 回退到远程地址
    if (request.getRemoteAddress() != null && request.getRemoteAddress().getAddress() != null) {
      return request.getRemoteAddress().getAddress().getHostAddress();
    }
    return "unknown";
  }

  /**
   * 路径脱敏（移除路径中的潜在 ID/Token）
   *
   * <p>将 /api/project/12345 统一为 /api/project/{id}， 便于审计日志聚合分析。
   *
   * @param path 原始路径
   * @return 脱敏后的路径
   */
  private String sanitizePath(String path) {
    if (path == null) {
      return "/";
    }

    // 将纯数字路径段替换为 {id}
    return path.replaceAll("/\\d+(/|$)", "/{id}$1")
        // 将 UUID 格式替换为 {uuid}
        .replaceAll(
            "/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}(/|$)", "/{uuid}$1");
  }


  /**
   * 过滤器顺序：+35，在限流(+30)之后
   *
   * <p>晚于限流确保被限流的请求不会记录审计（避免重复日志）。
   *
   * @return 顺序值
   */
  @Override
  public int getOrder() {
    return GatewayFilterOrder.AUDIT_LOG.getOrder();
  }
}
