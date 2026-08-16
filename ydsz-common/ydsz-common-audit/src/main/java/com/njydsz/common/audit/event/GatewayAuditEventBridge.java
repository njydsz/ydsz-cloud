package com.njydsz.common.audit.event;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;

import reactor.core.publisher.Mono;

/**
 * 网关审计事件桥接器
 *
 * <p>解决Spring Cloud Gateway（WebFlux 响应式）与 Spring MVC（Servlet + AOP）
 * 之间的审计数据互通问题。
 *
 * <p>使用场景：
 * <ul>
 *   <li>网关过滤器（{@code GlobalFilter}）无法直接注入 {@code AuditRecorder}（强依赖 Servlet 上下文）</li>
 *   <li>通过本桥接器，网关发布 {@link GatewayAuditEvent} 到
 *       {@link ApplicationEventPublisher}，由已有的 {@link AuditEventListener}
 *       异步消费并落库到 {@code sys_audit_log}</li>
 * </ul>
 *
 * <h3>使用模式：</h3>
 * <pre>{@code
 * // 在 Gateway Filter 中
 * auditEventBridge.publishAuditEvent(
 *     userId,
 *     clientIp,
 *     "DELETE",
 *     "/api/project/{id}",
 *     200,
 *     45L,
 *     traceId,
 *     tenantId
 * );
 * }</pre>
 *
 * <h3>集成架构：</h3>
 * <pre>
 * Gateway Filter → GatewayAuditEventBridge → ApplicationEventPublisher
 *     → @Async AuditEventListener → AuditRecorder → sys_audit_log
 * </pre>
 *
 * <p><b>设计要点：</b>
 * <ul>
 *   <li>桥接器发布操作日志事件（OperationLogEvent），复用已有的审计消费链路</li>
 *   <li>网关侧调用返回 Mono<Void>，支持响应式链式调用</li>
 *   <li>内部使用非阻塞发布（不调用 block()），通过 Reactor 事件循环调度</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.2.0
 */
public class GatewayAuditEventBridge {

    private static final Logger log = LoggerFactory.getLogger(GatewayAuditEventBridge.class);

    private final ApplicationEventPublisher eventPublisher;

    /**
     * 构造网关审计事件桥接器
     *
     * @param eventSpring 事件发布器
     */
    public GatewayAuditEventBridge(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    /**
     * 发布网关审计事件（响应式）
     *
     * <p>从 WebFlux 的非阻塞线程安全发布到 Spring 事件体系。
     *
     * @param userId    用户 ID
     * @param clientIp  客户端 IP
     * @param method    HTTP 方法
     * @param path      请求路径
     * @param statusCode HTTP 状态码
     * @param durationMs 请求耗时
     * @param traceId   追踪 ID
     * @param tenantId  租户 ID
     * @return 发布完成的 Mono
     */
    public Mono<Void> publishAuditEvent(String userId, String clientIp,
                                        String method, String path,
                                        int statusCode, long durationMs,
                                        String traceId, String tenantId) {
        return Mono.fromRunnable(() -> {
            try {
                boolean isWriteOperation = isWriteOperation(method);
                String status = (statusCode >= 200 && statusCode < 400) ? "SUCCESS" : "FAILED";

                // 构建审计事件
                OperationLogEvent event = OperationLogEvent.builder()
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

                if (log.isDebugEnabled()) {
                    log.debug("[GatewayAudit] 审计事件已发布: userId={}, method={}, path={}, status={}, duration={}ms",
                            userId, method, path, statusCode, durationMs);
                }
            } catch (Exception e) {
                log.error("[GatewayAudit] 发布审计事件异常: reason={}", e.getMessage(), e);
            }
        });
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
        switch (method.toUpperCase()) {
            case "POST":
            case "PUT":
            case "DELETE":
            case "PATCH":
                return true;
            default:
                return false;
        }
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
        switch (method.toUpperCase()) {
            case "POST":
                return "CREATE";
            case "PUT":
            case "PATCH":
                return "UPDATE";
            case "DELETE":
                return "DELETE";
            case "GET":
                return "QUERY";
            default:
                return "OTHER";
        }
    }
}
