package com.njydsz.common.safe.audit;

import java.time.Instant;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import com.njydsz.common.core.context.RequestContext;
import com.njydsz.common.json.YdszJson;
import com.njydsz.common.safe.alert.SecurityEvent;

import java.util.HashMap;
/**
 * 安全审计日志记录器
 *
 * <p>将安全事件以结构化 JSON 格式输出到独立的审计日志，
 * 支持 traceId 关联，可与 Loki/Sentry 集成。
 *
 * <p>traceId 优先从 {@link RequestContext}（统一上下文主源）读取，
 * 回退 MDC（兼容 Brave / 旧逻辑写入的 B3 traceId）。
 *
 * <p><b>日志格式：</b>
 * <pre>{@code
 * {
 *   "timestamp": "2026-07-15T10:30:00Z",
 *   "traceId": "abc123",
 *   "eventType": "XSS_ATTACK",
 *   "severity": "HIGH",
 *   "requestUri": "/api/users",
 *   "sourceIp": "192.168.1.1",
 *   "userAgent": "Mozilla/5.0...",
 *   "payload": "<script>alert(1)</script>",
 *   "details": {}
 * }
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class SecurityAuditLogger {

    private static final Logger auditLog = LoggerFactory.getLogger("SECURITY_AUDIT");

    /**
     * 解析当前链路 traceId：优先 {@link RequestContext}，回退 MDC 的 traceId / X-B3-TraceId。
     *
     * @return 当前 traceId；均不存在时返回 null
     */
    private static String resolveTraceId() {
        String traceId = RequestContext.getTraceId();
        if (traceId != null && !traceId.isEmpty()) {
            return traceId;
        }
        traceId = MDC.get("traceId");
        if (traceId == null || traceId.isEmpty()) {
            traceId = MDC.get("X-B3-TraceId");
        }
        return traceId;
    }

    /**
     * 记录安全事件审计日志
     *
     * @param event 安全事件
     */
    public void log(SecurityEvent event) {
        if (event == null) {
            return;
        }

        String traceId = resolveTraceId();

        Map<String, Object> logEntry = Map.of(
                "timestamp", event.getTimestamp() != null ? event.getTimestamp().toString() : Instant.now().toString(),
                "traceId", traceId != null ? traceId : "",
                "eventType", event.getEventType() != null ? event.getEventType().name() : "UNKNOWN",
                "severity", event.getSeverity() != null ? event.getSeverity().name() : "MEDIUM",
                "requestUri", event.getRequestUri() != null ? event.getRequestUri() : "",
                "sourceIp", event.getSourceIp() != null ? event.getSourceIp() : "",
                "userAgent", event.getUserAgent() != null ? event.getUserAgent() : "",
                "payload", event.getAttackPayload() != null ? event.getAttackPayload() : ""
        );

        auditLog.warn(YdszJson.toJson(logEntry));
    }

    /**
     * 记录自定义安全审计日志
     *
     * @param action    操作类型
     * @param sourceIp  来源 IP
     * @param userId    操作用户 ID
     * @param details   详细信息
     */
    public void log(String action, String sourceIp, String userId, Map<String, Object> details) {
        String traceId = resolveTraceId();

        Map<String, Object> logEntry = new HashMap<>();
        logEntry.put("timestamp", Instant.now().toString());
        logEntry.put("traceId", traceId != null ? traceId : "");
        logEntry.put("action", action);
        logEntry.put("sourceIp", sourceIp != null ? sourceIp : "");
        logEntry.put("userId", userId != null ? userId : "");
        if (details != null) {
            logEntry.put("details", details);
        }

        auditLog.info(YdszJson.toJson(logEntry));
    }
}
