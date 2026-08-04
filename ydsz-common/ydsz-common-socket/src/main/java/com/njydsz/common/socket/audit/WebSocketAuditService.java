package com.njydsz.common.socket.audit;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import com.njydsz.common.json.YdszJson;
import com.njydsz.common.socket.trace.WebSocketTraceContext;

/**
 * WebSocket 审计日志服务（P2-5）。
 *
 * <p>记录连接建立/断开、消息推送的结构化审计日志，
 * 通过专用 Logger {@code WS_AUDIT} 输出，便于日志采集和合规审计。
 *
 * <p>审计字段：
 * <ul>
 *   <li>timestamp — 时间戳</li>
 *   <li>traceId — 链路追踪 ID</li>
 *   <li>event — 事件类型（CONNECT / DISCONNECT / PUSH）</li>
 *   <li>userId — 用户 ID（脱敏）</li>
 *   <li>sessionId — Session ID</li>
 *   <li>pushType — 推送类型（PUSH 事件）</li>
 *   <li>success — 是否成功</li>
 *   <li>durationMs — 耗时（毫秒）</li>
 *   <li>error — 错误信息（失败时）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class WebSocketAuditService {

    /** 专用审计 Logger */
    private static final Logger AUDIT_LOG = LoggerFactory.getLogger("WS_AUDIT");

    /** 用户 ID 脱敏保留长度 */
    private static final int MASK_KEEP_LENGTH = 3;

    /**
     * 审计连接建立事件。
     *
     * @param userId    用户 ID
     * @param sessionId Session ID
     * @param remoteIp  远程 IP
     */
    public void auditConnect(String userId, String sessionId, String remoteIp) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("timestamp", Instant.now().toString());
        entry.put("traceId", MDC.get(WebSocketTraceContext.TRACE_ID_KEY));
        entry.put("event", "CONNECT");
        entry.put("userId_mask", maskUserId(userId));
        entry.put("sessionId", sessionId);
        entry.put("remoteIp", remoteIp);
        AUDIT_LOG.info(YdszJson.toJson(entry));
    }

    /**
     * 审计连接断开事件。
     *
     * @param userId    用户 ID
     * @param sessionId Session ID
     * @param durationMs 连接时长（毫秒）
     */
    public void auditDisconnect(String userId, String sessionId, long durationMs) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("timestamp", Instant.now().toString());
        entry.put("traceId", MDC.get(WebSocketTraceContext.TRACE_ID_KEY));
        entry.put("event", "DISCONNECT");
        entry.put("userId_mask", maskUserId(userId));
        entry.put("sessionId", sessionId);
        entry.put("durationMs", durationMs);
        AUDIT_LOG.info(YdszJson.toJson(entry));
    }

    /**
     * 审计消息推送事件。
     *
     * @param pushType  推送类型
     * @param userId    目标用户 ID（广播时为 null）
     * @param topic     目标主题（主题推送时使用）
     * @param success   是否成功
     * @param durationMs 耗时（毫秒）
     * @param error     错误信息（失败时）
     */
    public void auditPush(String pushType, String userId, String topic,
                          boolean success, long durationMs, String error) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("timestamp", Instant.now().toString());
        entry.put("traceId", MDC.get(WebSocketTraceContext.TRACE_ID_KEY));
        entry.put("event", "PUSH");
        entry.put("pushType", pushType);
        if (userId != null) {
            entry.put("userId_mask", maskUserId(userId));
        }
        if (topic != null) {
            entry.put("topic", topic);
        }
        entry.put("success", success);
        entry.put("durationMs", durationMs);
        if (error != null) {
            entry.put("error", truncate(error, 500));
        }
        AUDIT_LOG.info(YdszJson.toJson(entry));
    }

    /**
     * 用户 ID 脱敏。
     */
    private String maskUserId(String userId) {
        if (userId == null || userId.isEmpty()) {
            return "null";
        }
        if (userId.length() <= MASK_KEEP_LENGTH) {
            return "***";
        }
        return userId.substring(0, MASK_KEEP_LENGTH) + "***";
    }

    /**
     * 截断字符串。
     */
    private String truncate(String value, int max) {
        return value.length() > max ? value.substring(0, max) + "..." : value;
    }
}
