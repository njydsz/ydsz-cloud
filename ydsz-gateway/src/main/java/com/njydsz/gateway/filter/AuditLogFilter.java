package com.njydsz.gateway.filter;

import java.time.Instant;
import java.util.Set;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import com.njydsz.common.core.constant.HeaderConstants;
import com.njydsz.gateway.config.GatewayConstants;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * P2-2: 审计日志过滤器
 *
 * <p>记录安全相关的操作日志，满足合规审计要求（如等保/GDPR）。
 *
 * <h3>审计范围</h3>
 * <ul>
 *   <li>所有非 GET 请求（POST/PUT/DELETE/PATCH）— 写操作</li>
 *   <li>认证相关请求（/auth/**、/api/login 等）— 身份事件</li>
 *   <li>管理类接口（/admin/**、/api/admin/**）— 权限变更</li>
 *   <li>安全事件（401/403 响应）— 越权尝试</li>
 * </ul>
 *
 * <h3>审计内容</h3>
 * <ul>
 *   <li>操作时间（UTC ISO-8601）</li>
 *   <li>操作人（userId / IP / sessionId）</li>
 *   <li>操作类型（HTTP method + path）</li>
 *   <li>操作结果（HTTP status）</li>
 *   <li>用户设备（User-Agent / 客户端指纹）</li>
 * </ul>
 *
 * <h3>输出格式</h3>
 * <p>结构化 JSON 日志，便于 ELK/Loki 采集分析：
 * <pre>
 * {
 *   "eventType": "AUDIT",
 *   "timestamp": "2025-08-09T10:30:00Z",
 *   "userId": "u123456",
 *   "clientIp": "10.0.0.1",
 *   "method": "DELETE",
 *   "path": "/api/project/123",
 *   "statusCode": 200,
 *   "userAgent": "Mozilla/5.0...",
 *   "traceId": "abc123",
 *   "sensitivity": "HIGH"
 * }
 * </pre>
 *
 * <h3>执行顺序</h3>
 * <p>{@code HIGHEST_PRECEDENCE + 35}，在限流(+30)之后，
 * 在 AccessLogGlobalFilter 之后（使用其 traceId 关联）。
 *
 * @since 1.0.0 (P2-2)
 * @author ydsz-team
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "ydsz.gateway.filter", name = "audit-log", havingValue = "true", matchIfMissing = true)
public class AuditLogFilter implements GlobalFilter, Ordered {

    /** 审计敏感路径前缀（写操作/管理接口/鉴权接口） */
    private static final Set<String> AUDIT_SENSITIVE_PREFIXES = Set.of(
            "/auth",
            "/api/admin",
            "/admin",
            "/api/role",
            "/api/permission",
            "/api/user/create",
            "/api/user/delete",
            "/api/tenant",
            "/api/apikey"
    );

    /** 高敏感 HTTP 方法（DELETE 删除、PATCH 部分更新、PUT 全量更新） */
    private static final Set<HttpMethod> HIGH_SENSITIVITY_METHODS = Set.of(
            HttpMethod.DELETE,
            HttpMethod.PATCH,
            HttpMethod.PUT
    );

    /**
     * 审计日志过滤器入口
     *
     * <p>在请求完成后记录审计日志，使用 {@code then()} 确保
     * 审计不影响正常请求处理。
     *
     * @param exchange 服务器 Web 交换上下文
     * @param chain    网关过滤器链
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

        return chain.filter(exchange).then(Mono.fromRunnable(() -> {
            // 请求完成后记录审计日志
            int statusCode = exchange.getResponse().getStatusCode() != null
                    ? exchange.getResponse().getStatusCode().value() : 0;
            long duration = System.currentTimeMillis() - startTime;

            writeAuditLog(exchange, method, path, statusCode, duration);
        }));
    }

    /**
     * 写入审计日志
     *
     * <p>使用 StringBuilder 手动构建 JSON，避免 Map + 反射开销。
     *
     * @param exchange   服务器 Web 交换上下文
     * @param method     HTTP 方法
     * @param path       请求路径
     * @param statusCode HTTP 状态码
     * @param duration   请求耗时（毫秒）
     */
    private void writeAuditLog(ServerWebExchange exchange, HttpMethod method,
                                String path, int statusCode, long duration) {
        ServerHttpRequest request = exchange.getRequest();

        String userId = request.getHeaders().getFirst(GatewayConstants.HEADER_USER_ID);
        String traceId = request.getHeaders().getFirst(GatewayConstants.HEADER_TRACE_ID);
        String clientIp = extractClientIp(request);
        String userAgent = request.getHeaders().getFirst("User-Agent");
        String tenantId = request.getHeaders().getFirst(HeaderConstants.X_TENANT_ID);

        // User-Agent 截断
        if (userAgent != null && userAgent.length() > 100) {
            userAgent = userAgent.substring(0, 100);
        }

        // 脱敏路径（移除敏感信息）
        String sanitizedPath = sanitizePath(path);

        // 敏感级别：HIGH（高敏感操作）/ MEDIUM（一般写操作）/ LOW（安全事件如 401）
        String sensitivity = resolveSensitivity(method, path, statusCode);

        // 构建结构化 JSON 日志
        // 格式参考：OWASP AppSensor / NIST SP 800-92 审计日志规范
        StringBuilder sb = new StringBuilder(300);
        sb.append("{\"eventType\":\"AUDIT\"");
        sb.append(",\"timestamp\":\"").append(Instant.now().toString()).append("\"");
        sb.append(",\"traceId\":\"").append(safeJson(traceId)).append("\"");
        sb.append(",\"userId\":\"").append(safeJson(userId)).append("\"");
        sb.append(",\"tenantId\":\"").append(safeJson(tenantId)).append("\"");
        sb.append(",\"clientIp\":\"").append(safeJson(clientIp)).append("\"");
        sb.append(",\"method\":\"").append(method.name()).append("\"");
        sb.append(",\"path\":\"").append(safeJson(sanitizedPath)).append("\"");
        sb.append(",\"statusCode\":").append(statusCode);
        sb.append(",\"durationMs\":").append(duration);
        sb.append(",\"userAgent\":\"").append(safeJson(userAgent)).append("\"");
        sb.append(",\"sensitivity\":\"").append(sensitivity).append("\"");
        sb.append("}");

        String auditLog = sb.toString();

        // 按敏感级别和响应状态选择日志级别
        if ("HIGH".equals(sensitivity) || statusCode == 401 || statusCode == 403) {
            log.warn(auditLog);
        } else {
            log.info(auditLog);
        }
    }

    /**
     * 判断请求是否需要审计
     *
     * @param method HTTP 方法
     * @param path   请求路径
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
     * @param method     HTTP 方法
     * @param path       请求路径
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
        if (request.getRemoteAddress() != null
                && request.getRemoteAddress().getAddress() != null) {
            return request.getRemoteAddress().getAddress().getHostAddress();
        }
        return "unknown";
    }

    /**
     * 路径脱敏（移除路径中的潜在 ID/Token）
     *
     * <p>将 /api/project/12345 统一为 /api/project/{id}，
     * 便于审计日志聚合分析。
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
                .replaceAll("/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}(/|$)",
                        "/{uuid}$1");
    }

    /**
     * JSON 字符串转义（防止日志注入）
     *
     * @param input 输入字符串
     * @return 转义后的字符串（null 返回空字符串）
     */
    private String safeJson(String input) {
        if (input == null) {
            return "";
        }
        return input.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
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
        return Ordered.HIGHEST_PRECEDENCE + 35;
    }
}
